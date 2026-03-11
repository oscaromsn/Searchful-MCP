================================================================================
#	General remarks
================================================================================

Follow the instructions in one of the sections below to start DocFetcher. Each
section applies to a different version of the software.

Minimum requirements:
- Windows: Windows 7 SP1 or later, 64-bit only.
- Linux: GTK 3. GTK 2 is not supported. 64-bit only.
- macOS: macOS 11 or later.

The main difference between the non-portable and portable version of DocFetcher
lies in where the application's settings and indexes are stored:
- non-portable: somewhere in your home folder
- portable: in the application folder

In case of issues, you can look for more information and help in the following
places:
- Manual: Can be found in the "help" folder inside the DocFetcher folder.
- Website: https://docfetcher.sourceforge.io/
- Forum: https://sourceforge.net/p/docfetcher/discussion/702424/
- Wiki: https://sourceforge.net/p/docfetcher/wiki/Home/


================================================================================
#	DocFetcher for Windows, non-portable (installed)
================================================================================

After running the installer, launch the application via the DocFetcher shortcut
in your start menu.


================================================================================
#	DocFetcher for Windows, portable
================================================================================

After unpacking the downloaded archive, go into the new DocFetcher folder and
double-click on DocFetcher.exe to launch the application.

Beware:

1) The application may not work properly if you extract it into a folder for
which you don't have write permissions, e.g., "C:\Program Files".

2) Do not unpack the files on top of an existing DocFetcher installation. This
will get program files mixed up, potentially causing abnormal program behavior.

To transfer your settings and indexes from an older version of portable
DocFetcher to the current version, copy *only* the folders "conf" and
"indexes" from the old application folder into the new one. Additionally, carry
over the ".bat" file if you modified it.


================================================================================
#	DocFetcher for Linux, non-portable
================================================================================

After unpacking the downloaded archive, go into the new DocFetcher directory and
double-click on the script DocFetcher.sh to launch the application.

If the script doesn't run or a text editor shows up, make sure the executable
flag has been set on it. If that doesn't work either, you might see a helpful
error message if you start the script from the terminal.

Beware: Do not unpack the files on top of an existing DocFetcher installation.
This will get program files mixed up, potentially causing abnormal program
behavior.


================================================================================
#	DocFetcher for Linux, portable
================================================================================

After unpacking the downloaded archive, go into the new DocFetcher directory and
double-click on the script DocFetcher.sh to launch the application.

If the script doesn't run or a text editor shows up, make sure the executable
flag has been set on it. If that doesn't work either, you might see a helpful
error message if you start the script from the terminal.

Beware:

1) The application may not work properly if you extract it into a directory for
which you don't have write permissions.

2) Do not unpack the files on top of an existing DocFetcher installation. This
will get program files mixed up, potentially causing abnormal program behavior.

To transfer your settings and indexes from an older version of portable
DocFetcher to the current version, copy *only* the directories "conf" and
"indexes" from the old application directory into the new one.


================================================================================
#	DocFetcher for macOS, non-portable (application bundle)
================================================================================

After mounting and opening the disk image (the .dmg file), move the DocFetcher
application bundle to a location of your choice. Usually, this would be your
Applications folder, but any folder for which you have write permissions will
do.

Do not move the application bundle on top of an existing DocFetcher application
bundle. This will get program files mixed up, potentially causing abnormal
program behavior.

To start DocFetcher, double-click the application bundle.


================================================================================
#	DocFetcher for macOS, portable
================================================================================

After mounting and opening the disk image (the .dmg file), move the DocFetcher
application folder to a location of your choice.

Beware:

1) The application may not work properly if you move it to a location for which
you don't have write permissions.

2) Do not move the application on top of an existing DocFetcher installation.
This will get program files mixed up, potentially causing abnormal program
behavior.

Next, remove the quarantine flag from the entire application folder:
- Open a terminal and use the 'cd' command to navigate to the application
  folder.
- Run: xattr -r -d com.apple.quarantine .

Now double-click 'DocFetcher.command' to launch the application.

If you use the command file and it doesn't work, ensure that the executable flag
is set on it: chmod +x ./DocFetcher.command

Note that double-clicking the 'DocFetcher.app' application bundle does *not*
work, as it's not a fully functional application bundle.

Double-clicking the command file may leave a terminal window open. Preventing
that requires terminal-specific configuration. In the default terminal, it can
be done as follows:
- Go to: Terminal > Settings > Profiles > Shell
- Set 'When the shell exits' to 'Close if the shell exited cleanly'.

To transfer your settings and indexes from an older version of portable
DocFetcher to the current version, copy *only* the folders "conf" and
"indexes" from the old application folder into the new one.
