#!/usr/bin/env python3

"""
On Linux, this script requires installing MUSL:
rustup target add x86_64-unknown-linux-musl
"""

#===============================================================================
# Utility functions
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

def call(*args):
	import subprocess as sub
	p = sub.Popen(flatten_args(*args))
	p.communicate()
	return p.wait()

# None -> string
def get_script_dir():
	import os.path as osp, sys
	return osp.dirname(osp.abspath(sys.argv[0]))

# None -> None
def change_to_script_dir():
	import os
	os.chdir(get_script_dir())


#===============================================================================
# Execution
#===============================================================================

import platform

platform_name = platform.system()
is_windows = platform_name == "Windows"
is_linux = platform_name == "Linux"
is_macos = platform_name == "Darwin"

change_to_script_dir()
if is_windows or is_macos:
	ret = call("cargo", "build", "--release")
elif is_linux:
	ret = call("cargo", "build", "--release", "--target=x86_64-unknown-linux-musl")
	if ret != 0:
		exit(1)
	ret = call("cargo", "build", "--release", "--target=x86_64-apple-darwin")
if ret != 0:
	exit(ret)