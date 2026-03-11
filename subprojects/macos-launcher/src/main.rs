use std::env;
use std::fs;
use std::io::Write;
use std::os::unix::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{Command, exit};
use serde::Deserialize;

#[derive(Deserialize, Debug)]
struct LaunchConfig {
    app_name: String,
    main_class: String,
    working_directory: String,
    user_dir_name: String,
    lib_path: String,
    lib_path_property: String,
    lang_path: String,
    default_memory_limit: String,
}

/// Read memory limit from file, trimming whitespace and comments
fn read_memory_limit_from_file(path: &Path) -> Option<String> {
    if let Ok(content) = fs::read_to_string(path) {
        // Find the first non-empty, non-comment line
        for line in content.lines() {
            let trimmed = line.trim();
            if !trimmed.is_empty() && !trimmed.starts_with('#') {
                if is_valid_memory_limit(trimmed) {
                    return Some(trimmed.to_string());
                }
            }
        }
    }
    None
}

/// Check if a string is a valid memory limit format (e.g., "4g", "2048m")
fn is_valid_memory_limit(s: &str) -> bool {
    if s.is_empty() {
        return false;
    }

    // Check if it ends with a valid unit (k, m, g, K, M, G)
    let last_char = s.chars().last().unwrap();
    if !['k', 'K', 'm', 'M', 'g', 'G'].contains(&last_char) {
        return false;
    }

    // Check if everything before the unit is a number
    let number_part = &s[..s.len() - 1];
    number_part.parse::<u64>().is_ok()
}

/// Create memory limit file with default value and comments
fn create_default_memory_limit_file(path: &Path, default_value: &str) {
    // Ensure parent directory exists
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }

    let content = format!(
        "# This sets the maximum amount of memory the application can use.\n\
         # Valid formats: <number>k, <number>m, <number>g (e.g., 512m, 4g)\n\
         # Default: 16g\n\
         {}\n",
        default_value
    );

    if let Ok(mut file) = fs::File::create(path) {
        let _ = file.write_all(content.as_bytes());
    }
}

/// Get the memory limit to use, checking external file first, then falling back to default
fn get_memory_limit(working_dir: &Path, config: &LaunchConfig) -> String {
    // Determine if we're in portable mode by checking for indexes/.indexes.txt
    let indexes_marker = working_dir.join("indexes/.indexes.txt");
    let is_portable = indexes_marker.exists();

    // Determine the path to memory-limit.txt
    let memory_limit_path: PathBuf = if is_portable {
        // Portable: <working_dir>/conf/memory-limit.txt
        working_dir.join("conf/memory-limit.txt")
    } else {
        // Non-portable: ~/<user_dir_name>/conf/memory-limit.txt
        if let Some(home) = env::var_os("HOME") {
            Path::new(&home)
                .join(&config.user_dir_name)
                .join("conf/memory-limit.txt")
        } else {
            // Fallback if HOME is not set
            return config.default_memory_limit.clone();
        }
    };

    // Try to read from the external file
    if let Some(limit) = read_memory_limit_from_file(&memory_limit_path) {
        return limit;
    }

    // File doesn't exist or is invalid, create it with default value
    create_default_memory_limit_file(&memory_limit_path, &config.default_memory_limit);

    // Return the default limit
    config.default_memory_limit.clone()
}

fn main() {
    let args: Vec<String> = env::args().collect();
    
    let current_exe = env::current_exe().expect("Failed to get current executable path");
    let current_dir = current_exe.parent().expect("Failed to get executable directory");
    let config_path = current_dir.join("../Resources/launch-config.json");
    
    let config = match fs::read_to_string(&config_path) {
        Ok(content) => {
            serde_json::from_str::<LaunchConfig>(&content)
                .unwrap_or_else(|e| {
                    eprintln!("Failed to parse launch config: {}", e);
                    exit(1);
                })
        },
        Err(e) => {
            eprintln!("Failed to read launch config at {}: {}", config_path.display(), e);
            exit(1);
        }
    };

    let java_path = current_dir.join("../Frameworks/jre.bundle/Contents/Home/bin/java");
    let lib_path = current_dir.join(&config.lib_path);
    let working_dir = current_dir.join(&config.working_directory);
    let memory_limit = get_memory_limit(&working_dir, &config);

    // Build classpath by collecting all .jar files in lib directory
    let mut classpath = String::new();
    match fs::read_dir(&lib_path) {
        Ok(entries) => {
            for entry in entries {
                if let Ok(entry) = entry {
                    let path = entry.path();
                    if let Some(extension) = path.extension() {
                        if extension == "jar" {
                            if !classpath.is_empty() {
                                classpath.push(':');
                            }
                            classpath.push_str(&path.to_string_lossy());
                        }
                    }
                }
            }
        },
        Err(e) => {
            eprintln!("Failed to read lib directory: {}", e);
            exit(1);
        }
    }

    if classpath.is_empty() {
        eprintln!("No JAR files found in lib directory: {}", lib_path.display());
        exit(1);
    }

    // Launch Java with the same arguments as the shell scripts
    let mut cmd = Command::new(&java_path);
    cmd.arg("-XstartOnFirstThread")
       .arg(format!("-Xdock:name={}", config.app_name))
       .arg("-enableassertions")
       .arg(format!("-Xmx{}", memory_limit))
       .arg("-cp")
       .arg(format!(".:{}:{}", config.lang_path, classpath))
       .arg(format!("-Djava.library.path={}", config.lib_path_property))
       .arg("-Dorg.eclipse.swt.display.useSystemTheme=true")
       .arg(&config.main_class);

    // Add any additional command line arguments
    if args.len() > 1 {
        cmd.args(&args[1..]);
    }

    // Change to working directory before launching
    cmd.current_dir(&working_dir);

    // Use exec() to replace the current process (like shell scripts do)
    let error = cmd.exec();
    // exec() only returns if there was an error
    eprintln!("Failed to execute Java: {}", error);
    exit(1);
}