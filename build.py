#!/usr/bin/env python3

#===============================================================================
# Build: Compilation
#===============================================================================

import os, os.path as osp, re, shutil, sys
from datetime import datetime
from os.path import join
from build_lib import *

# Change current directory to script directory
os.chdir(osp.dirname(osp.abspath(sys.argv[0])))

# Set up constants
app_name = "DocFetcher"
version = to_lines("current-version.txt", True, True, "#")[0]
major_version = version.split(".")[0] + ".0"
package = "net.sourceforge.docfetcher"
package_sub_path = join(*package.split("."))
main_class = "Main"
start_time = datetime.now()
build_dir = "build"
build_time = start_time.strftime("%Y%m%d-%H%M%S")
windows_signing_config_path = "./code-signing-windows.txt"
macos_signing_config_path = "./code-signing-macos.txt"

skip_classes = [
	"net.sourceforge.docfetcher.man",
]
skip_jar_regexes = [
	r".*-sources\.jar"  # Source JARs
]

# Files to sign in the macOS builds
MACOS_STANDALONE_FILES_TO_SIGN = [
	"lib/libjnotify.jnilib"
]

# Files inside JARs to sign in the macOS builds
MACOS_JAR_FILES_TO_SIGN = [
	("lib/jna-3.2.7.jar", "com/sun/jna/darwin/libjnidispatch.jnilib"),
]

skip_recompilation = False
compile_only = False
skip_notarization = False

if "skip" in sys.argv[1:]:
	skip_recompilation = True
elif "compile" in sys.argv[1:]:
	compile_only = True
else:
	print(
		"Hint: To skip recompilation, " +
		"run this script with a 'skip' argument."
	)
	print(
		"Hint: To only compile the main jar, " +
		"run this script with a 'compile' argument."
	)

if "unnotarized" in sys.argv[1:]:
	skip_notarization = True
elif is_linux and osp.isfile(macos_signing_config_path):
	print(
		"Hint: To skip macOS code signing and notarization, " +
		"run this script with an 'unnotarized' argument."
	)

# Fail if build directory is a file
if osp.isfile(build_dir):
	fail("Can't create build directory, a file with that name already exists.")

jre_windows, jre_linux, jre_macos = get_jre_dirs("build-jre.txt")
convert_line_endings_in_dir("dist")

print("Cleaning build directory...")
do_skip_recompilation = clean_dir(
	build_dir,
	["classes"] if skip_recompilation else []
)

# On Windows, write the version number and nothing else into a new text file, so
# that the version number can be easily read by the NSIS compiler when creating
# the Windows installer.
if is_windows:
	to_file(join("build/version.txt"), version)

all_jar_files = collect_jar_files("lib")
host_jar_files = filter_platform_jars(all_jar_files)

print("Checking jar file Eclipse signatures...")
signature_filenames = ['META-INF/ECLIPSE_.RSA', 'META-INF/ECLIPSE_.SF']
failed_jar_files = []
for jar_file in host_jar_files:
	if not check_jar_eclipse_signatures(jar_file, signature_filenames):
		failed_jar_files.append(jar_file)
if failed_jar_files:
	msg = [
		"Please remove these Eclipse signature files " +
		"from the jar files below:"
	]
	for filename in signature_filenames:
		msg.append(f"- {filename}")
	msg.append("Jar files:")
	for jar_file in failed_jar_files:
		msg.append(f"- {jar_file}")
	fail("\n".join(msg))

if do_skip_recompilation:
	print("Skipping compilation...".upper())
else:
	compile(
		build_dir, package_sub_path, main_class, host_jar_files,
		skip_classes
	)

print("Creating jar archives...")
program_conf_dest = join(
	build_dir, "classes", package_sub_path,
	"program-conf.txt"
)
os.makedirs(osp.dirname(program_conf_dest), exist_ok=True)
shutil.copyfile("dist/program-conf.txt", program_conf_dest)
settings_conf_dest = join(
	build_dir, "classes", package_sub_path,
	"enums", "settings-conf-header.txt"
)
os.makedirs(osp.dirname(settings_conf_dest), exist_ok=True)
shutil.copyfile(
	"src/net/sourceforge/docfetcher/enums/settings-conf-header.txt",
	settings_conf_dest
)
shutil.copyfile(
	"src/log4j2.xml",
	join(build_dir, "classes", "log4j2.xml")
)
tika_mime_path = join(build_dir, "classes", "org", "apache", "tika", "mime")
os.makedirs(tika_mime_path, exist_ok=True)
shutil.copyfile(
	"src/org/apache/tika/mime/tika-mimetypes.xml",
	join(tika_mime_path, "tika-mimetypes.xml")
)
def create_jar(is_portable: bool) -> str:
	classes_dir = join(build_dir, "classes")
	sys_conf_str = strip_lines("""
		ProgramName=%s
		IsPortable=%s
		ProgramVersion=%s
		BuildDate=%s
	""") % (app_name, str(is_portable).lower(), version, build_time)
	to_file(
		join(classes_dir, package_sub_path, "system-conf.txt"), sys_conf_str
	)
	edition = "portable" if is_portable else "nonportable"
	main_jar_file = join(
		build_dir,
		"%s-%s-%s_%s.jar" % (package, version, edition, build_time)
	)
	# Create JAR with all compiled classes
	package_root = package.split(".")[0]  # "net" from "net.sourceforge.docfetcher"
	call(
		"jar", "cfe",
		main_jar_file,
		package + "." + main_class,
		"-C", classes_dir, package_root
	)
	# Add remaining directories (org, etc.) to the JAR
	for name in os.listdir(classes_dir):
		if name == package_root:
			continue
		call("jar", "uf", main_jar_file, "-C", classes_dir, name)
	return main_jar_file
main_jar_file_portable = create_jar(True)
main_jar_file_nonportable = create_jar(False)

if compile_only:
	exit(0)

# Compile macOS launcher subproject when building for macOS
if is_linux or is_macos:
	compile_subproject("macos-launcher")

licenses_file = deploy_licenses(build_dir)


#===============================================================================
# Build utilities
#===============================================================================

# string, boolean, boolean -> None
def create_base_build(dst, is_portable, force_macos=False):
	if is_portable:
		os.makedirs(join(dst, "conf"), exist_ok=True)
		shutil.copyfile(
			"dist/program-conf.txt",
			join(dst, "conf/program-conf.txt")
		)
	
	# Copy prebuilt documentation from dist/help to dst/doc
	help_dir = "dist/help"
	if osp.isdir(help_dir):
		doc_dst = join(dst, "help")
		os.makedirs(doc_dst, exist_ok=True)
		help_langs = [d for d in os.listdir(help_dir) if osp.isdir(join(help_dir, d))]
		if help_langs:
			print(f"Copying documentation...")
			for lang_name in help_langs:
				lang_src = join(help_dir, lang_name)
				dst_lang = join(doc_dst, lang_name)
				if osp.exists(dst_lang):
					shutil.rmtree(dst_lang)
				shutil.copytree(lang_src, dst_lang)
		else:
			print("Warning: No documentation languages found in dist/help")
	else:
		print("Warning: dist/help directory not found - no documentation will be included")

	# Copy language resource files from dist/lang to dst/lang
	lang_src_dir = "dist/lang"
	if osp.isdir(lang_src_dir):
		print("Copying language resource files...")
		lang_dst_dir = join(dst, "lang")
		if osp.exists(lang_dst_dir):
			shutil.rmtree(lang_dst_dir)
		shutil.copytree(lang_src_dir, lang_dst_dir)
	else:
		print("Warning: dist/lang directory not found - no language resources will be included")

	copy_img_files("dist/img", join(dst, "img"))
	
	os.makedirs(join(dst, "lib"), exist_ok=True)
	info_str = strip_lines("""
		major_version=%s
		is_portable=%s
	""") % (major_version, str(is_portable).lower())
	to_file(join(dst, "lib", "daemon-info.txt"), info_str)
	
	os.makedirs(join(dst, "misc"), exist_ok=True)
	shutil.copyfile(licenses_file, join(dst, "misc/licenses.zip"))
	shutil.copyfile("dist/paths.txt", join(dst, "misc/paths.txt"))
	
	shutil.copyfile(
		"dist/Readme.txt",
		join(dst, "Readme.txt")
	)

	shutil.copytree(join("dist", "py4j"), join(dst, "py4j"))
	shutil.copyfile(join("dist", "search.py"), join(dst, "search.py"))
	make_executable(join(dst, "search.py"))
	
	# Create an empty file 'indexes/.indexes.txt' to let the daemon know we're
	# the portable version.
	if is_portable:
		os.makedirs(join(dst, "indexes"), exist_ok=True)
		to_file(join(dst, "indexes/.indexes.txt"), "")
	
	if is_portable:
		main_jar_file = main_jar_file_portable
	else:
		main_jar_file = main_jar_file_nonportable
	
	target_platform = None
	if force_macos or is_macos:
		target_platform = "macos"
	elif is_windows:
		target_platform = "windows"
	elif is_linux:
		target_platform = "linux"

	config = PLATFORM_JAR_CONFIG[target_platform]
	native_ext = config["native_ext"]
	target_jar_files = filter_platform_jars(all_jar_files, target_platform)

	native_lib_files = collect_files_by_extension("lib", [native_ext])
	for lib_file in [main_jar_file] + target_jar_files + native_lib_files:
		name = osp.basename(lib_file)
		if any((re.fullmatch(r, name) for r in skip_jar_regexes)):
			continue
		shutil.copyfile(lib_file, join(dst, "lib", name))

# for non-Windows platforms
# string, string -> None
def copy_compiled_executables(dst_dir, path_templ):
	# Copy DocFetcher daemon
	src_daemon = "dist/daemon/docfetcher-daemon-linux"
	if osp.isfile(src_daemon):
		dst_daemon = join(dst_dir, "docfetcher-daemon-linux")
		shutil.copyfile(src_daemon, dst_daemon)
		make_executable(dst_daemon)


#===============================================================================
# Build: Windows Portable
#===============================================================================

if is_windows:
	# Track JRE paths for exclusion from code signing
	jre_paths = []

	for is_portable in [True, False]:
		portable_str = "Portable" if is_portable else "Non-Portable"
		print("Creating Windows %s build..." % portable_str)
		app_dirname = "%s-%s-Windows-64bit-%s" % (
			app_name, version, portable_str.replace("-", "")
		)
		app_dir = join(build_dir, app_dirname)
		create_base_build(app_dir, is_portable)
		
		shutil.copyfile(
			join("dist/launchers", "DocFetcher-4096.exe"),
			join(app_dir, app_name + ".exe")
		)
		
		# Copy DocFetcher batch launcher
		copy_shell_script(
			"dist/launchers/launcher.bat",
			join(app_dir, "%s.bat" % app_name),
			subst = {"main_class": package + "." + main_class}
		)
		# Copy DocFetcher Windows executables
		launcher_files = [
			"DocFetcher-256.exe",
			"DocFetcher-512.exe",
			"DocFetcher-1024.exe",
			"DocFetcher-2048.exe",
			"DocFetcher-4096.exe",
			"DocFetcher-8192.exe",
			"DocFetcher-12288.exe",
			"DocFetcher-16384.exe"
		]
		for name in launcher_files:
			src_path = join("dist/launchers", name)
			if osp.isfile(src_path):
				shutil.copyfile(src_path, join(app_dir, "misc", name))
		
		# Copy DocFetcher daemon executable
		src_daemon = "dist/daemon/docfetcher-daemon-windows.exe"
		if osp.isfile(src_daemon):
			shutil.copyfile(
				src_daemon, join(app_dir, "docfetcher-daemon-windows.exe")
			)
		
		jre_path = join(app_dir, "jre")
		add_jre(jre_windows, jre_path)
		jre_paths.append(jre_path)
		if is_portable:
			zip_path = join(build_dir, app_dirname + ".zip")
			zip_dir(app_dir, zip_path, True)
		else:
			print("NOTE: Run the NSIS script to create the Windows installer.")
	
	if shutil.which("signtool.exe") is None:
		print(
			"NOTE: signtool.exe not found on PATH. "
			"Code signing will be skipped. "
			"To enable signing, install the Windows SDK, "
			"add signtool.exe to your PATH and "
			f"create a file '{osp.basename(windows_signing_config_path)}' "
			"containing the SHA1 certificate thumbprint."
		)
	elif not osp.isfile(windows_signing_config_path):
		print(
			"NOTE: Code signing certificate file not found: "
			f"{windows_signing_config_path}. "
			"Code signing will be skipped. "
			"To enable signing, create the aforementioned file "
			"and put the SHA1 certificate thumbprint in it."
		)
	else:
		with open(windows_signing_config_path, 'r', encoding='utf-8') as f:
			thumbprint = f.read().strip()
		if not thumbprint:
			fail(
				f"Expected a thumbprint in {windows_signing_config_path}, "
				"found nothing."
			)
		sign_code_files_on_windows(thumbprint, build_dir, jre_paths)


#===============================================================================
# Build: Linux (Portable + Non-Portable)
#===============================================================================

if is_linux:
	for is_portable in [True, False]:
		portable_str = "Portable" if is_portable else "Non-Portable"
		print("Creating Linux %s build..." % portable_str)
		app_dirname = "%s-%s-Linux-64bit-%s" % (
			app_name, version, portable_str.replace("-", "")
		)
		app_dir = join(build_dir, app_dirname)
		create_base_build(app_dir, is_portable)
		
		# Copy DocFetcher Linux launcher
		src_path = join("dist/launchers", "launcher-linux.sh")
		if osp.isfile(src_path):
			copy_shell_script(
				src_path, join(app_dir, "%s.sh" % app_name),
				subst = {"main_class": package + "." + main_class},
				subst_ignore = ["classpath", "f"]
			)
		
		copy_compiled_executables(app_dir, "")
		
		add_jre(jre_linux, join(app_dir, "jre"))
		zip_path = join(build_dir, app_dirname + ".zip")
		zip_dir(app_dir, zip_path, True)


#===============================================================================
# Build: macOS (Portable + Non-Portable)
#===============================================================================

def copy_macos_launcher(
	src_path_templ: str,
	dst_path: str,
	lib_path: str,
	lib_path_prop: str,
	lang_path: str,
	working_directory: str,
	config_dir: str
) -> None:
	import json

	# Copy executable
	subproject = "macos-launcher"
	src_path = src_path_templ % (subproject, subproject)
	os.makedirs(osp.dirname(dst_path), exist_ok=True)
	shutil.copyfile(src_path, dst_path)
	make_executable(dst_path)

	# Create launch configuration
	user_dir_name = "." + app_name.lower()
	config = {
		"app_name": app_name,
		"main_class": package + "." + main_class,
		"working_directory": working_directory,
		"user_dir_name": user_dir_name,
		"lib_path": lib_path,
		"lib_path_property": lib_path_prop,
		"lang_path": lang_path,
		"default_memory_limit": "4g",
	}
	os.makedirs(config_dir, exist_ok=True)
	with open(osp.join(config_dir, "launch-config.json"), 'w') as f:
		json.dump(config, f, indent=2)

def copy_info_plist(dst_path: str) -> None:
	shutil.copyfile("dist/Info.plist", dst_path)
	subst = {
		"app_name": app_name,
		"app_version": version,
		"package_id": package
	}
	substitute_in_file(dst_path, subst)

if is_linux or is_macos:
	# Check for code signing config when cross-compiling from Linux to macOS.
	macos_signing_config = None
	if is_linux:
		if skip_notarization:
			print("Skipping macOS code signing and notarization as requested.")
		else:
			macos_signing_config = parse_macos_signing_config(
				macos_signing_config_path
			)
			if macos_signing_config is not None:
				check_rcodesign_availability()
				validate_macos_signing_config_files(
					macos_signing_config_path, macos_signing_config
				)

	exe_path_templ = "subprojects/%s/target/x86_64-apple-darwin/release/%s" \
		if is_linux \
		else "subprojects/%s/target/release/%s"

	print("Creating macOS Portable build...")
	app_dirname = "%s-%s-macOS-64bit-Portable" % (app_name, version)
	app_dir = join(build_dir, app_dirname)
	create_base_build(app_dir, True, True)

	copy_macos_launcher(
		exe_path_templ,
		join(app_dir, "%s.app/Contents/MacOS/%s" % (app_name, app_name)),
		"../../../lib",
		"lib",
		"lang",
		"../../..",
		join(app_dir, "%s.app/Contents/Resources" % app_name)
	)

	# Create shell script launcher
	shell_script_content = "\n".join([
		"#!/bin/bash",
		'scriptdir=$(cd "$(dirname "$0")"; pwd)',
		'cd "$scriptdir"',
		f"nohup './{app_name}.app/Contents/MacOS/{app_name}' &>/dev/null &",
		"exit"
	])
	# Use '.command' extension so the script can be run via double-click
	shell_script_path = join(app_dir, app_name + ".command")
	to_file(shell_script_path, shell_script_content)
	make_executable(shell_script_path)

	copy_info_plist(join(
		app_dir, "%s.app/Contents/Info.plist" % app_name
	))

	dst_path = join(
		app_dir, "%s.app/Contents/Resources/img/app.icns" % app_name
	)
	os.makedirs(osp.dirname(dst_path), exist_ok=True)
	shutil.copyfile("dist/app.icns", dst_path)

	add_jre(
		jre_macos,
		join(app_dir, f"{app_name}.app/Contents/Frameworks/jre.bundle")
	)

	if macos_signing_config is not None:
		sign_binaries_in_macos_build(
			app_dir, macos_signing_config,
			MACOS_STANDALONE_FILES_TO_SIGN, MACOS_JAR_FILES_TO_SIGN
		)
		app_bundle_path = join(app_dir, "%s.app" % app_name)
		sign_app_bundle_with_rcodesign(app_bundle_path, macos_signing_config)
	
	if is_macos:
		# Temporarily move the entire application directory into a new
		# directory so that the hdiutil command will put the application
		# directory itself rather than its contents in the disk image.
		app_dir_tmp = join(build_dir, "app_dir_tmp")
		os.makedirs(app_dir_tmp, exist_ok=True)
		shutil.move(app_dir, app_dir_tmp)
		
		dmg_name = "%s-%s-macOS-64bit-Portable.dmg" % (app_name, version)
		vol_name = "%s %s Portable" % (app_name, version)
		create_dmg(app_dir_tmp, join(build_dir, dmg_name), vol_name)
		shutil.move(join(app_dir_tmp, app_dirname), build_dir)
		shutil.rmtree(app_dir_tmp)
	
	print("Creating macOS Application Bundle build...")
	app_dirname = "%s.app" % app_name
	app_dir = join(build_dir, app_dirname)
	resources_dir = join(app_dir, "Contents/Resources")
	create_base_build(resources_dir, False, True)

	copy_macos_launcher(
		exe_path_templ,
		join(app_dir, "Contents/MacOS/%s" % app_name),
		"../Resources/lib",
		"../Resources/lib",
		"../Resources/lang",
		".",
		join(app_dir, "Contents/Resources")
	)

	copy_info_plist(join(app_dir, "Contents/Info.plist"))

	shutil.copyfile(
		"dist/app.icns",
		join(resources_dir, "img/app.icns")
	)

	add_jre(jre_macos, join(app_dir, "Contents/Frameworks/jre.bundle"))

	if macos_signing_config is not None:
		sign_binaries_in_macos_build(
			resources_dir, macos_signing_config,
			MACOS_STANDALONE_FILES_TO_SIGN, MACOS_JAR_FILES_TO_SIGN
		)
		sign_app_bundle_with_rcodesign(app_dir, macos_signing_config)
	
	if is_macos:
		app_dir_tmp = join(build_dir, "app_dir_tmp")
		os.makedirs(app_dir_tmp, exist_ok=True)
		shutil.move(app_dir, app_dir_tmp)
		
		shutil.copyfile(
			"dist/Readme.txt",
			join(app_dir_tmp, "Readme.txt")
		)
		
		dmg_name = "%s-%s-macOS-64bit-NonPortable.dmg" % (app_name, version)
		vol_name = "%s %s Non-Portable" % (app_name, version)
		create_dmg(app_dir_tmp, join(build_dir, dmg_name), vol_name)
		shutil.move(join(app_dir_tmp, app_dirname), build_dir)
		shutil.rmtree(app_dir_tmp)
	
	if is_linux:
		print("NOTE: Run build-dmg.py to create .dmg files for macOS.")
		if macos_signing_config is not None:
			print(
				"NOTE: After creating .dmg files, "
				"run dmg-notarize.py to sign and notarize them."
			)
		elif skip_notarization:
			print("NOTE: The macOS builds are unsigned.")
		else:
			print(
				"NOTE: The macOS builds are unsigned. "
				"To enable signing, create a file named "
				f"'{osp.basename(macos_signing_config_path)}'."
			)
	elif is_macos:
		print(
			"NOTE: The macOS builds are unsigned. "
			"Signing is currently only implemented "
			"for cross-compilation from Linux to macOS."
		)

dur = datetime.now() - start_time
print("Total elapsed time: " + to_human_readable_duration(dur))
