#!/usr/bin/env python3

#===============================================================================
# Global variables
#===============================================================================

import platform

platform_name = platform.system()
is_windows = platform_name == "Windows"
is_linux = platform_name == "Linux"
# WARNING: This flag does not apply when cross-building from Linux to macOS!
is_macos = platform_name == "Darwin"

# Platform-specific JAR configuration
PLATFORM_JAR_CONFIG: dict[str, dict[str, str]] = {
	"windows": {
		"swt_pattern": r".*win32-win32.*",
		"native_ext": "dll"
	},
	"linux": {
		"swt_pattern": r".*gtk-linux.*",
		"native_ext": "so"
	},
	"macos": {
		"swt_pattern": r".*cocoa-macosx.*",
		"native_ext": "jnilib"
	}
}


#===============================================================================
# General utility functions
#===============================================================================

# *args -> [string]
def flatten_args(*args):
	args2 = []
	for arg in args:
		if isinstance(arg, (list, tuple)):
			args2 += flatten_args(*arg)
		else:
			args2.append(str(arg))
	return args2

# ([string] | *string) -> int
def call(*args):
	import subprocess as sub
	p = sub.Popen(flatten_args(*args))
	p.communicate()
	return p.wait()

# ([string] | *string) -> int
def call_silent(*args):
	import os, subprocess as sub
	DEVNULL = open(os.devnull, "wb")
	p = sub.Popen(flatten_args(*args), stdout=DEVNULL, stderr=DEVNULL)
	p.communicate()
	return p.wait()

# [string] -> string
def get_output(*args):
	import subprocess as sub
	process = sub.Popen(flatten_args(*args), stdout=sub.PIPE, stderr=sub.PIPE)
	output, errors = process.communicate()
	return output.decode("utf-8").strip()

# None -> int
def get_term_width():
	import os
	return os.get_terminal_size().columns

# string, string -> string
def strip_lines(string, line_sep="\n"):
	s = string.strip()
	lines = []
	for line in s.splitlines():
		lines.append(line.strip())
	return line_sep.join(lines)

# string, boolean, boolean, string, string -> [string]
def to_lines(file_path, strip=False, omit_empty=False, comment_char="", encoding="utf-8"):
	with open(file_path, "r", encoding=encoding) as f:
		if not strip and not omit_empty:
			lines = [x.rstrip("\r\n") for x in f.readlines()]
			if comment_char != "":
				lines = [x for x in lines if not x.lstrip().startswith(comment_char)]
			return lines
		lines = []
		for line in f.readlines():
			if strip:
				line = line.strip()
				if comment_char != "" and line.startswith(comment_char):
					continue
			else:
				line = line.rstrip("\r\n")
				if comment_char != "" and line.lstrip().startswith(comment_char):
					continue
			if omit_empty and line == "":
				continue
			lines.append(line)
		return lines

def to_props(file_path: str) -> dict[str, str]:
	"""Assuming the given file is a properties file, loads the "key=value" lines
	in it and returns them as a dictionary. Commenting out lines using the '#'
	character is supported. Blank lines and lines not containing a '=' character
	are ignored. In case of duplicate keys, keys found later are ignored.
	Leading and trailing whitespace is stripped from the keys and values.
	"""
	d = dict()
	for line in to_lines(file_path, True, True, "#"):
		parts = line.split("=", 1)
		if len(parts) != 2:
			continue
		key = parts[0].rstrip()
		if key in d:
			continue
		d[key] = parts[1].lstrip()
	return d

# string, (string | [string]), boolean -> None
def to_file(file_path, string_or_lines, overwrite=False):
	if overwrite:
		import os, os.path as osp
		if osp.isfile(file_path):
			os.remove(file_path)
	with open(file_path, "w", encoding="utf-8") as f:
		if isinstance(string_or_lines, str):
			f.write(string_or_lines)
		else:
			f.write("\n".join(string_or_lines))

# string, string -> None
def copy_dir_contents(src_dir, dst_dir):
	import os, os.path as osp, shutil
	os.makedirs(dst_dir, exist_ok=True)
	for name in sorted(os.listdir(src_dir)):
		src_path = osp.join(src_dir, name)
		dst_path = osp.join(dst_dir, name)
		if osp.isfile(src_path):
			shutil.copyfile(src_path, dst_path)
		else:
			shutil.copytree(src_path, dst_path)

# string, string, boolean -> None
def zip_dir(src, dst, include_top=False):
	"""Puts the contents of the directory 'src' in the zip file 'dst'.
	
	The zip directory structure will be relative to the source directory.
	Depending on whether 'include_top' is True, the source directory will be the
	top entry in the zip archive.
	"""
	import os, os.path as osp, zipfile
	zf = zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED)
	src = osp.abspath(src)
	if include_top:
		prefix = osp.dirname(src)
	else:
		prefix = src
	for root, dirs, files in os.walk(src, True):
		for filename in sorted(files):
			file_path = osp.join(root, filename)
			entry_path = osp.relpath(file_path, prefix)
			zf.write(file_path, entry_path)
	zf.close()

# datetime.timedelta -> string
def to_human_readable_duration(td):
	secs = td.total_seconds()
	hrs = secs // 3600
	secs -= hrs * 3600
	mins = secs // 60
	secs -= mins * 60
	ret = ""
	if hrs != 0:
		ret += "%d h" % hrs
	if mins != 0:
		ret += ("" if hrs == 0 else " ") + "%d min" % mins
	if secs != 0:
		ret += ("" if hrs == 0 and mins == 0 else " ") + "%d s" % secs
	return "0 s" if ret == "" else ret

# string -> None
def fail(msg):
	print("> Error: " + msg)
	print("Build failed.")
	exit(1)

# string -> boolean
def contains_files(root_dir):
	"""Recursively determines whether the given directory contains files.
	Subdirectories are not considered files."""
	import os
	for root, dirs, files in os.walk(root_dir):
		if len(files) > 0:
			return True
	return False

# string, [string], (string -> boolean) -> [string]
def collect_files_by_extension(root_dir, extensions=[], accept_fn=lambda x: True):
	import os, os.path as osp
	col = []
	for root, dirs, files in os.walk(root_dir):
		for filename in files:
			# string -> boolean
			def matches(ext):
				return filename.lower().endswith("." + ext.lower())
			path = osp.join(root, filename)
			if any((matches(ext) for ext in extensions)) and accept_fn(path):
				col.append(path)
	return col

# Example arguments: "/path/to/file.txt", {"key": "value"}, ["key_to_ignore"]
# In the example, "${key}" will be replaced with "value".
# string, {string: string}, [string] -> None
def substitute_in_file(filepath, subst_table={}, subst_ignore=[]):
	import os.path as osp, re
	# Read file
	text = None
	with open(filepath, "r") as f:
		text = f.read()
	
	# Perform text substitutions
	# Check for keys missing in file
	filename = osp.basename(filepath)
	for key, value in subst_table.items():
		if subst_ignore and key in subst_ignore:
			continue
		key = "${%s}" % key
		if not key in text:
			fail("Key '%s' not found in file '%s'."\
				% (key, filename))
		text = text.replace(key, value)
	
	# Check for unsubstituted keys in file
	m = re.match(r".*?\${([^}]*)}.*", text, re.DOTALL)
	if m:
		key = m.group(1)
		if not key in subst_ignore:
			fail("Unused key '${%s}' in file '%s'." \
				% (m.group(1), filename))
	
	# Write to file
	with open(filepath, "w") as f:
		f.write(text)

# string, string, string -> None
def copyfile_convert_line_endings(src_path, dst_path, ls):
	# src_path and dst_path may be identical
	with open(src_path, "r", newline=None) as src_file:
		lines = src_file.readlines()
	with open(dst_path, "w", newline=ls) as dst_file:
		dst_file.writelines(lines)


#===============================================================================
# Build-related utility functions
#===============================================================================

def sign_code_files_on_windows(
	thumbprint: str,
	root_dir: str,
	exclude_paths: list[str] = None
) -> None:
	"""Recursively scans the given directory for exe, dll, and msi files and
	signs them using signtool. This function must be run on Windows.
	
	Args:
		thumbprint: SHA1 signing certificate thumbprint
		root_dir: Root directory to scan for files to sign
		exclude_paths: List of paths to exclude from signing
	"""
	import os, os.path as osp
	assert is_windows

	# Collect all files that need to be signed
	all_files = collect_files_by_extension(root_dir, ["exe", "dll", "msi"])
	
	# Filter out excluded paths
	files_to_sign = []
	
	if exclude_paths:
		# Normalize exclude paths for comparison
		normalized_excludes = [
			osp.normpath(osp.abspath(path)) for path in exclude_paths
		]
		
		for file_path in all_files:
			normalized_file = osp.normpath(osp.abspath(file_path))
			should_exclude = False
			
			for exclude_path in normalized_excludes:
				if normalized_file.startswith(exclude_path + osp.sep) or \
						normalized_file == exclude_path:
					should_exclude = True
					break
			
			if not should_exclude:
				files_to_sign.append(file_path)
	else:
		files_to_sign = all_files
	
	if not files_to_sign:
		fail("No exe or dll files found to sign in directory: %s" % root_dir)
	
	for file_path in files_to_sign:
		ret = call(
			"signtool", "sign",
			"/sha1", thumbprint,
			"/tr", "http://time.certum.pl",
			"/td", "sha256",
			"/fd", "sha256",
			"/v",
			file_path
		)
		if ret != 0:
			fail(f"Failed to sign file: {file_path}")

# string -> string
def slashes_to_dots(path):
	"""Converts a file path, e.g., '/path/to/file.txt', to a Java package path,
	e.g., 'path.to.file'. This function replaces backslashes and forward slashes
	with dots, and strips leading and trailing slashes."""
	import re
	return re.sub(r"\\|/", ".", path).strip(".")

# string -> (string, string, string)
def get_jre_dirs(jre_file):
	import os.path as osp
	jre_comment = """
# Paths to the Java runtimes to be bundled with the builds. For Windows and
# Linux, the paths must point to directories containing 'bin' and 'lib'
# subdirectories. For macOS, the path must point to a directory containing a
# 'Contents' subdirectory. The readme.txt file contains additional info about
# the bundled Java runtimes.
jre_windows=
jre_linux=
jre_macos=
	""".strip()
	if not osp.isfile(jre_file):
		to_file(jre_file, jre_comment)
		fail("No '%s' file was found, generated a new one. " % jre_file +\
			"Please fill out the paths in the file and try again.")
	jre_props = to_props(jre_file)
	# string -> string
	def get_jre_dir(key):
		path = jre_props.get(key, "")
		if path == "":
			return ""
		path = osp.expanduser(path)
		if not osp.isdir(path):
			fail("Invalid definition in '%s': '%s' is not a directory." % (jre_file, path))
		return path
	jre_windows = get_jre_dir("jre_windows")
	jre_linux = get_jre_dir("jre_linux")
	jre_macos = get_jre_dir("jre_macos")
	if is_windows and jre_windows == "":
		fail("Missing 'jre_windows' definition in %s." % jre_file)
	if is_linux and jre_linux == "":
		fail("Missing 'jre_linux' definition in %s." % jre_file)
	if (is_linux or is_macos) and jre_macos == "":
		fail("Missing 'jre_macos' definition in %s." % jre_file)
	return jre_windows, jre_linux, jre_macos

# string -> None
def convert_line_endings_in_dir(root_dir):
	import os, os.path as osp
	ext_to_line_sep = {
		"bat": "\r\n",
		"ini": "\r\n",
		"sh": "\n",
		"txt": "\r\n"
	}
	for root, dirs, files in os.walk(root_dir):
		for filename in files:
			ext = osp.splitext(filename)[1].lstrip(".").lower()
			if not ext in ext_to_line_sep:
				continue
			ls = ext_to_line_sep[ext]
			if is_windows and ls == "\n":
				continue
			path = osp.join(root, filename)
			copyfile_convert_line_endings(path, path, ls)

# string, [string] -> boolean
def clean_dir(build_dir, skip_dirnames=[]):
	"""Clears the contents of the given directory, creating it if it doesn't
	exist yet. If the 'skip_dirnames' argument is non-empty, any direct (!)
	subdirectories that contain files and whose names are in the 'skip_dirnames'
	list will be preserved along with their contents.
	
	This function returns whether any files were actually preserved as a result
	of the 'skip_dirnames' argument."""
	
	import os, os.path as osp, shutil
	os.makedirs(build_dir, exist_ok=True)
	
	# On Windows, we'll do a preliminary pass to remove only .exe and .dll
	# files. This may fail because the programs associated with these files may
	# still be running. In case of failure, abort the entire script.
	def remove_exe_or_fail(file_path):
		ext = osp.splitext(file_path)[1].lower()
		if not ext in [".exe", ".dll"]:
			return
		try:
			os.remove(file_path)
		except OSError:
			msg = "Cannot remove file, " + \
				"it may be locked by a running process:\n" + file_path
			fail(msg)
	if is_windows:
		for filename in os.listdir(build_dir):
			path = osp.join(build_dir, filename)
			if osp.isfile(path):
				remove_exe_or_fail(path)
			elif osp.isdir(path):
				if filename in skip_dirnames:
					continue
				for root, dirs, files in os.walk(path):
					for filename2 in files:
						path2 = osp.join(root, filename2)
						remove_exe_or_fail(path2)
	
	skipped_files = False
	for filename in os.listdir(build_dir):
		path = osp.join(build_dir, filename)
		if osp.isfile(path):
			os.remove(path)
		elif osp.isdir(path):
			if filename in skip_dirnames and contains_files(path):
				skipped_files = True
			else:
				# Without 'ignore_errors', this can fail on Windows, no idea why
				shutil.rmtree(path, ignore_errors=True)
	return skipped_files

# string -> [string]
def collect_jar_files(root_dir):
	"""Collects and returns jar files and native libs under the given directory.
	Sources are not included."""
	import os.path as osp
	# string -> boolean
	def accept_fn(path):
		name_l = osp.basename(path).lower()
		for suffix in ["-sources.jar", "-src.jar"]:
			if name_l.endswith(suffix):
				return False
		return True
	return collect_files_by_extension(root_dir, ["jar"], accept_fn)

def filter_platform_jars(
	jar_list: list[str],
	platform: str | None = None,
	include_swt: bool = True
) -> list[str]:
	"""Filter platform-specific JARs to include only those matching the given
	platform. If the given platform is None, use the current platform.
	"""
	import os.path as osp
	import re

	if platform is None:
		if is_windows:
			platform = "windows"
		elif is_linux:
			platform = "linux"
		elif is_macos:
			platform = "macos"
		else:
			fail("Unsupported platform.")

	config = PLATFORM_JAR_CONFIG[platform]
	swt_pattern = config["swt_pattern"]

	filtered = []
	for jar_file in jar_list:
		name = osp.basename(jar_file)
		include_jar = True

		# Filter SWT jars by platform (or exclude entirely)
		if re.fullmatch(r"swt-.*\.jar", name):
			if not include_swt:
				include_jar = False
			elif not re.match(swt_pattern, name):
				include_jar = False

		if include_jar:
			filtered.append(jar_file)

	return filtered

def check_jar_eclipse_signatures(
	jar_path: str,
	signature_filenames: list[str]
) -> bool:
	import zipfile
	try:
		with zipfile.ZipFile(jar_path, 'r') as jar:
			jar_contents = jar.namelist()
			for signature_filename in signature_filenames:
				if signature_filename in jar_contents:
					return False
		return True
	except zipfile.BadZipFile:
		# Skip files that aren't valid zip/jar files
		return True

# Example strings for skip_classes argument:
# - com.docfetcherpro.gui
# - com.docfetcherpro.MainDesktop
def compile(
		build_dir: str,
		package_sub_path: str,
		main_class: str,
		jar_files: [str],
		skip_classes: [str]):
	import os, os.path as osp, shutil
	from datetime import datetime
	from os.path import join
	
	src_dir = join(build_dir, "sources")
	dst_dir = join(build_dir, "classes")
	shutil.copytree("src", src_dir)
	os.makedirs(dst_dir, exist_ok=True)
	
	# Remove copied source files that are marked as to be skipped
	for root, dirs, files in os.walk(src_dir):
		for dirname in dirs:
			path = join(root, dirname)
			if slashes_to_dots(osp.relpath(path, src_dir)) in skip_classes:
				shutil.rmtree(path)
		for filename in files:
			name_no_ext, ext = osp.splitext(filename)
			if ext != ".java":
				continue
			path_no_ext = join(root, name_no_ext)
			pkg_path = slashes_to_dots(osp.relpath(path_no_ext, src_dir))
			if pkg_path in skip_classes:
				os.remove(join(root, filename))
	
	# Copy Tika mime types file to classes dir so it ends up in the jar file.
	# Without the mime types file, parsing of RTF files may fail.
	mimetypes_subpath = "org/apache/tika/mime/tika-mimetypes.xml"
	src_mimetypes_file = join(src_dir, mimetypes_subpath)
	dst_mimetypes_file = join(dst_dir, mimetypes_subpath)
	os.makedirs(osp.dirname(dst_mimetypes_file), exist_ok=True)
	shutil.copyfile(src_mimetypes_file, dst_mimetypes_file)
	
	print("Compiling source files with javac...")
	compile_paths = collect_files_by_extension(src_dir, ["java"])
	start = datetime.now()
	classpath_sep = ";" if is_windows else ":"
	# Need to write out the compiler arguments to a file instead of passing
	# them directly, otherwise the build may fail on Windows due to an
	# argument length limit.
	args = [
		# Setting UTF-8 is needed on Windows, otherwise CP-1252 may be used,
		# making the compilation fail on Unicode source files, e.g., some
		# files from Tika.
		"-encoding", "UTF8",
		"-sourcepath", src_dir,
		"-d", dst_dir,
		"-classpath", classpath_sep.join(jar_files),
		"-source", "17",
		"-nowarn",
	] + compile_paths
	to_file(join(build_dir, "args-javac.txt"), "\n".join(args))
	# Using call_silent here because javac prints "notes" when there are
	# compilation warnings, despite the "-nowarn" argument. The compilation
	# warnings come from the imported Tika source code, so all we can do is
	# ignore them.
	call("javac", "@" + join(build_dir, "args-javac.txt"))
	duration = datetime.now() - start
	print("Compilation with javac took %d s." % duration.seconds)

# string -> string
def deploy_licenses(build_dir):
	import os, os.path as osp, shutil
	from os.path import join
	
	licenses_dir = join(build_dir, "licenses")
	licenses_file = licenses_dir + ".zip"
	os.makedirs(licenses_dir, exist_ok=True)
	
	# string -> boolean
	def is_license(filename):
		fname = filename.lower()
		for match_name in ["license", "licence", "lgpl", "about.html", "copying"]:
			if match_name in fname:
				return True
		return False
	
	# Create directories and copy license files
	for root, dirs, files in os.walk("lib"):
		for dirname in dirs:
			rel_path = osp.relpath(join(root, dirname), "lib")
			os.makedirs(join(licenses_dir, rel_path), exist_ok=True)
		for filename in files:
			if is_license(filename):
				src_path = join(root, filename)
				rel_path = osp.relpath(src_path, "lib")
				dst_path = join(licenses_dir, rel_path)
				shutil.copyfile(src_path, dst_path)
	
	# Remove empty directories
	for root, dirs, files in os.walk(licenses_dir, False):
		for dirname in dirs:
			dirpath = join(root, dirname)
			if len(os.listdir(dirpath)) == 0:
				os.rmdir(dirpath)
	
	# Special case: Copy lib/swt/about_files directory
	about_files_dir_dst = join(licenses_dir, "swt/about_files")
	if osp.isdir(about_files_dir_dst):
		shutil.rmtree(about_files_dir_dst)
	shutil.copytree(
		"lib/swt/about_files",
		about_files_dir_dst
	)
	
	# Create zip archive
	zip_dir(licenses_dir, licenses_file, False)
	shutil.rmtree(licenses_dir)
	return licenses_file

# string, string, string -> None
def create_dmg(src_path, dst_path, vol_name):
	import os, os.path as osp
	from os.path import join
	
	tmp_path = join(osp.dirname(dst_path), "tmp.dmg")
	# Create intermediate disk image
	ret = call_silent(
		"hdiutil",
		"create",
		"-ov", # overwrite existing file
		"-srcfolder", src_path,
		"-fs", "HFS+",
		"-volname", vol_name,
		tmp_path
	)
	if ret != 0:
		fail("Can't create disk image.")
	# Compress with bzip2
	ret = call_silent(
		"hdiutil",
		"convert",
		"-ov", # overwrite existing file
		tmp_path,
		"-format", "UDBZ",
		"-o", dst_path
	)
	os.remove(tmp_path)
	if ret != 0:
		fail("Can't create disk image.")


#===============================================================================
# Minor build-related utility functions
#===============================================================================

def add_jre(jre_dir: str, target_jre_path: str) -> None:
	import os, os.path as osp, shutil
	from os.path import join
	
	if osp.isdir(target_jre_path):
		shutil.rmtree(target_jre_path)
	copy_dir_contents(jre_dir, target_jre_path)
	
	# Set executable flags
	if is_windows:
		return
	bin_dirs = [
		join(target_jre_path, "bin"),
		join(target_jre_path, "Contents/Home/bin")
	]
	for bin_dir in bin_dirs:
		if not osp.isdir(bin_dir):
			continue
		for name in os.listdir(bin_dir):
			call("chmod", "+x", join(bin_dir, name))

def make_executable(file_path: str) -> None:
	if is_linux or is_macos:
		call("chmod", "+x", file_path)

def copy_shell_script(
	src_file: str,
	dst_file: str,
	subst: dict[str, str] = {},
	subst_ignore: list[str] = []
) -> None:
	import os, os.path as osp, shutil
	os.makedirs(osp.dirname(dst_file), exist_ok=True)
	shutil.copyfile(src_file, dst_file)
	substitute_in_file(dst_file, subst, subst_ignore)
	make_executable(dst_file)

def copy_img_files(src_dir: str, dst_dir: str) -> None:
	import os.path as osp, shutil
	# string, [string] -> [string]
	def ignore_img_files(src, names):
		ignored_names = []
		accepted_exts = {
			"bmp", "gif", "ico", "jpeg", "jpg", "png", "tif", "tiff"
		}
		for name in names:
			ext = osp.splitext(name)[1].lower().lstrip(".")
			if not ext in accepted_exts:
				ignored_names.append(name)
		return ignored_names
	shutil.copytree(src_dir, dst_dir, ignore=ignore_img_files)


#===============================================================================
# MacOS code signing functions
#===============================================================================

def check_rcodesign_availability() -> None:
	"""Check if rcodesign executable is available. Fails the build if not found.
	"""
	import shutil
	if shutil.which("rcodesign") is None:
		fail(
			"rcodesign executable not found. "
			"Please run 'cargo install apple-codesign' to install it."
		)

def parse_macos_signing_config(config_path: str) -> dict[str, str] | None:
	"""Parse the code-signing-macos.txt configuration file and return a
	dictionary of keys and values, or None if the file doesn't exist. Fail the
	build if the file exists, but its contents are invalid.
	"""
	import os.path as osp
	example_content = """
certificate_file=/path/to/certificate.p12
password_file=/path/to/certificate-password-file.txt
api_key_file=/path/to/api-key.json
	""".strip()

	if not osp.isfile(config_path):
		return None

	config = {}
	required_keys = ["certificate_file", "password_file", "api_key_file"]
	
	print("Loading macOS signing config...")
	try:
		lines = to_lines(
			config_path, strip=True, omit_empty=True, comment_char="#"
		)
		for line in lines:
			if "=" not in line:
				continue
			key, value = line.split("=", 1)
			key = key.strip()
			value = value.strip()
			if key in required_keys:
				config[key] = value
	except Exception as e:
		fail(f"Error reading configuration file: {e}")

	# Check if all required keys are present
	missing_keys = [key for key in required_keys if key not in config]
	if missing_keys:
		fail(
			f"Invalid format in '{osp.basename(config_path)}'. "
			f"Missing keys: {', '.join(missing_keys)}\n"
			"The file should have the following format:\n" +
			# Add some indentation to each line:
			"\n".join("  " + x for x in example_content.splitlines())
		)

	return config

def validate_macos_signing_config_files(
	config_path: str,
	config: dict[str, str]
) -> None:
	"""Validate that all files specified in the macOS configuration exist, and
	optionally expand paths.
	"""
	import os.path as osp
	for key, file_path in config.items():
		expanded_path = osp.expanduser(file_path)
		if not osp.isfile(expanded_path):
			fail(
				f"File specified in '{osp.basename(config_path)}' "
				f"does not exist: {file_path}"
			)
		# Update config with expanded path
		config[key] = expanded_path

def sign_file_with_rcodesign(file_path: str, config: dict[str, str]) -> None:
	"""Sign a single file using rcodesign."""
	import os.path as osp
	if not osp.isfile(file_path):
		fail(f"File to sign does not exist: {file_path}")

	ret = call(
		"rcodesign", "sign",
		"--p12-file", config["certificate_file"],
		"--p12-password-file", config["password_file"],
		"--code-signature-flags", "runtime",
		"--runtime-version", "22.0.0",  # macOS 13
		file_path
	)

	if ret != 0:
		fail(f"Failed to sign file: {file_path}")

def unpack_jar_file(jar_path: str, extract_dir: str) -> None:
	"""Unpack a JAR file to a directory."""
	import os, os.path as osp, shutil

	# Make paths absolute to avoid confusion
	abs_jar_path = osp.abspath(jar_path)
	abs_extract_dir = osp.abspath(extract_dir)

	if not osp.exists(abs_jar_path):
		fail(f"JAR file does not exist: {jar_path}")

	if osp.exists(abs_extract_dir):
		shutil.rmtree(abs_extract_dir)
	os.makedirs(abs_extract_dir, exist_ok=True)

	# Change to extract directory and extract jar there
	original_cwd = os.getcwd()
	try:
		os.chdir(abs_extract_dir)
		ret = call("jar", "xf", abs_jar_path)
		if ret != 0:
			fail(f"Failed to extract JAR file: {jar_path}")
	finally:
		os.chdir(original_cwd)

def repack_jar_file(jar_path: str, extract_dir: str) -> None:
	"""Repack a directory back into a JAR file."""
	import os, os.path as osp

	# Make paths absolute to avoid confusion
	abs_jar_path = osp.abspath(jar_path)
	abs_extract_dir = osp.abspath(extract_dir)

	if osp.exists(abs_jar_path):
		os.remove(abs_jar_path)

	# Change to the extract directory and create jar from there
	original_cwd = os.getcwd()
	try:
		os.chdir(abs_extract_dir)
		ret = call("jar", "cf", abs_jar_path, ".")
		if ret != 0:
			fail(f"Failed to create JAR file: {jar_path}")
	finally:
		os.chdir(original_cwd)

def sign_binaries_in_macos_build(
	build_dir: str,
	config: dict[str, str],
	standalone_files: list[str],
	jar_files: list[tuple[str, str]]
) -> None:
	"""Sign all required binaries in a macOS build directory.

	Args:
		build_dir: Root directory of the build
		config: macOS code signing configuration
		standalone_files: List of relative paths to standalone files to sign
		jar_files: List of (jar_path, inner_file_path) tuples for files inside JARs

	Raises:
		Fails the build immediately if any required file is not found.
	"""
	import os.path as osp, tempfile

	# Sign standalone files
	for rel_path in standalone_files:
		file_path = osp.join(build_dir, rel_path)
		if not osp.exists(file_path):
			fail(f"Required file for macOS code signing not found: {rel_path}")
		print(f"Signing: {rel_path}")
		sign_file_with_rcodesign(file_path, config)

	# Sign files inside JAR files
	for jar_rel_path, inner_file_path in jar_files:
		jar_path = osp.join(build_dir, jar_rel_path)
		if not osp.exists(jar_path):
			fail(
				"Required JAR file for macOS code signing "
				f"not found: {jar_rel_path}"
			)

		print(f"Processing JAR: {jar_rel_path}")

		# Create temporary directory for extraction
		with tempfile.TemporaryDirectory() as temp_dir:
			# Extract JAR
			unpack_jar_file(jar_path, temp_dir)

			# Sign the inner file
			inner_file_full_path = osp.join(temp_dir, inner_file_path)
			if not osp.exists(inner_file_full_path):
				fail(
					"Required file for macOS code signing "
					f"not found in {jar_rel_path}: {inner_file_path}"
				)
			print(f"  Signing: {inner_file_path}")
			sign_file_with_rcodesign(inner_file_full_path, config)

			# Repack JAR
			repack_jar_file(jar_path, temp_dir)

def sign_app_bundle_with_rcodesign(
	app_bundle_path: str, config: dict[str, str]
) -> None:
	"""Sign an app bundle (directory with .app extension) using rcodesign."""
	import os.path as osp
	if not osp.isdir(app_bundle_path):
		fail(f"App bundle does not exist: {app_bundle_path}")

	if not app_bundle_path.endswith('.app'):
		fail(f"Path does not appear to be an app bundle: {app_bundle_path}")

	print(f"Signing app bundle: {osp.basename(app_bundle_path)}")
	ret = call(
		"rcodesign", "sign",
		"--p12-file", config["certificate_file"],
		"--p12-password-file", config["password_file"],
		"--for-notarization",
		"--runtime-version", "22.0.0",  # macOS 13
		app_bundle_path
	)

	if ret != 0:
		fail(f"Failed to sign app bundle: {app_bundle_path}")

def compile_subproject(subproject):
	"""Compile a subproject using its build script."""
	print("Compiling subproject: %s..." % subproject)
	if is_windows:
		ret = call("python", "subprojects/%s/build.py" % subproject)
	else:
		ret = call("subprojects/%s/build.py" % subproject)
	if ret != 0:
		fail("Compilation of subproject failed: " + subproject)
