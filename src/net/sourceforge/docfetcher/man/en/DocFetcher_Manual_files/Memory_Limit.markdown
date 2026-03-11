How to raise the memory limit
=============================
DocFetcher has a default memory limit of 256&nbsp;MB, which is set on startup. The limit can be raised by fiddling with the platform-specific launchers:

Windows
-------
The Windows version of DocFetcher ships with ready-made alternative launchers that set different heap sizes. Follow these steps to use them:

* Open the DocFetcher folder. If you're using the portable version of DocFetcher, this is just the folder you downloaded and unpacked. If you're using the non-portable version, the DocFetcher folder will be in `C:\Program Files`, or `C:\Program Files (x86)`, or a similar location.
* The alternative launchers are inside the `DocFetcher\misc` folder. They are named `DocFetcher-XXX.exe`, where `XXX` is the heap size set by the respective launcher. For example, the launcher `DocFetcher-512.exe` will set a heap size of 512&nbsp;MB.
* Before you can use any of these launchers, **you must first move or copy it into the DocFetcher folder**. It's not necessary to delete the default launcher or to rename the alternative launcher.

Another way of changing the memory limit is to copy the file `misc\DocFetcher.bat` into the DocFetcher folder and alter the expression `-Xmx256m` in the last line of the file, for example to `-Xmx512m`.

Linux
-----
Open the launcher script `DocFetcher/DocFetcher.sh` with a text editor, and in the last line, alter the expression `-Xmx256m` as needed, for example to `-Xmx512m`.

Mac OS&nbsp;X
-------------
On Mac OS&nbsp;X, the memory limit is configured via a file named `memory-limit.txt`. The location of this file depends on which version you're using:

* **Portable version**: In the DocFetcher folder, open the file `conf/memory-limit.txt` with a text editor.
* **Non-portable version**: In your home directory, open the file `.docfetcher/conf/memory-limit.txt` with a text editor. Note that folders starting with a dot are hidden by default in Finder. To show hidden files, press `Cmd+Shift+.` (Command+Shift+period) in Finder, or use Terminal to navigate to the file.

If the file doesn't exist yet, launch DocFetcher once to have it created automatically. After opening the file:

* You'll see a value like `4g` below some comment lines. Change this to the desired memory limit, for example to `8g` for 8&nbsp;GB of RAM.
* Save and close the file, then close the program if it's still open, then restart the program.
