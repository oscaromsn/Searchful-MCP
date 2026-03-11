The "dmg" executable in this folder is a small Linux tool for compressing .dmg
files. A snapshot of the Git repository from which the dmg tool was built is
also included in this folder.

How to use the tool: ./dmg /path/to/in.dmg /path/to/out.dmg

How to build:
- Install zlib. On Ubuntu 18.04: sudo apt-get install zlib1g
- git clone https://github.com/fanquake/libdmg-hfsplus
- cd libdmg-hfsplus
- cmake .
- make -j8
Now the "dmg" executable can be found in the "dmg" subfolder.
