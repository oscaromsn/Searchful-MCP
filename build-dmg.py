#!/usr/bin/env python3

#===============================================================================
# Utility functions
#===============================================================================

# ([string] | *string) -> int
def call(*args):
	import subprocess as sub
	p = sub.Popen(flatten_args(*args))
	p.communicate()
	return p.wait()

# *args -> [string]
def flatten_args(*args):
	args2 = []
	for arg in args:
		if isinstance(arg, (list, tuple)):
			args2 += flatten_args(*arg)
		else:
			args2.append(str(arg))
	return args2

# [string] -> string
def get_output(*args):
	import subprocess as sub
	process = sub.Popen(flatten_args(*args), stdout=sub.PIPE, stderr=sub.PIPE)
	output, errors = process.communicate()
	return output.decode("utf-8").strip()

# Prints the given message and then exits with an error status. The message will
# be stripped.
# any -> None
def quit(msg):
	print(msg.strip())
	exit(1)

# None -> None
def require_non_root():
	import os
	if os.getuid() == 0:
		quit("Do not run this script as root.")

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


#===============================================================================
# Execution
#===============================================================================

import getpass, os, os.path as osp, shutil, sys
from os.path import join

require_non_root()

# Change current directory to script directory
os.chdir(osp.dirname(osp.abspath(sys.argv[0])))

app_name = "DocFetcher"
version = to_lines("current-version.txt", True, True, "#")[0]

src_names = [
	"%s.app" % app_name,
	"%s-%s-macOS-64bit-Portable" % (app_name, version),
]

for src_name in src_names:
	if not osp.isdir(join("build", src_name)):
		print("Please run the script build.py first.")
		exit(1)

for src_name in src_names:
	src_path = join("build", src_name)
	du_output = get_output("du", "-sk", src_path).split()[0]
	content_size_kb = int(du_output)
	
	# Calculate overhead: 15% of content size with a 5 MB minimum
	# This accounts for file system metadata, directory structure, and alignment
	overhead_percent = 0.15
	min_overhead_kb = 5 * 1024  # 5 MB minimum
	overhead_kb = max(int(content_size_kb * overhead_percent), min_overhead_kb)
	dir_size = content_size_kb + overhead_kb
	
	is_portable = "Portable" in src_name
	dst_name = "%s-%s-macOS-64bit-%s.dmg" % (
		app_name, version,
		"Portable" if is_portable else "NonPortable"
	)
	dst_path = join("build", dst_name)
	if osp.isfile(dst_path):
		os.remove(dst_path)
	call(
		"dd",
		"if=/dev/zero",
		"of=" + dst_path,
		"bs=1024",
		"count=" + str(dir_size)
	)
	vol_name = "%s %s %s" % (
		app_name, version,
		"Portable" if is_portable else "Non-Portable"
	)
	call("mkfs.hfsplus", "-v", vol_name, dst_path)
	
	mnt_path = "/mnt/tmp-" + app_name.lower()
	call("sudo", "mkdir", mnt_path)
	call("sudo", "mount", "-o", "loop", dst_path, mnt_path)
	
	call("sudo", "cp", "-r", src_path, mnt_path)
	if not is_portable:
		call(
			"sudo", "cp",
			"dist/Readme.txt",
			join(mnt_path, "Readme.txt")
		)
	user_name = getpass.getuser()
	call("sudo", "chown", "-R", "%s:%s" % (user_name, user_name), mnt_path)
	
	call("sudo", "umount", mnt_path)
	call("sudo", "rm", "-rf", mnt_path)
	
	dst2_path = dst_path + ".dmg"
	shutil.move(dst_path, dst2_path)
	call("chmod", "+x", "dev/dmg/dmg")
	call("dev/dmg/dmg", dst2_path, dst_path)
	os.remove(dst2_path)
	
	call("sudo", "chown", "%s:%s" % (user_name, user_name), dst_path)
