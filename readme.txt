#===============================================================================
#	Setup in IntelliJ IDEA
#===============================================================================

- The following instructions explain how to get this project running in IntelliJ
  IDEA.
- This project uses the Standard Widget Toolkit (SWT) for its user interface.
  Since SWT requires platform-specific JAR files, each developer needs to
  configure their environment to use the correct JAR for their operating system.
- First, open this project in IntelliJ IDEA.
- In IntelliJ IDEA, go to: Settings > Appearance & Behavior > Path Variables
- Add a new variable:
  - Name: SWT_JAR
  - Value: (Full path to your platform's SWT JAR file, see available JAR files
    under "./lib/swt".)
- To verify, go to: File > Project Structure > Libraries. You should see a
  library named "SWT" with the path showing your configured JAR. Additionally,
  go to: File > Project Structure > Modules > Dependencies. You should see "SWT"
  in the dependencies list.
- To run the application in IntelliJ IDEA, go to: Run > Edit Configurations.
  Then create a new run configuration of type "Application" and set the
  following attributes:
  - Name: DocFetcher
  - JDK or JRE: (Java 21 or newer)
  - Required VM options:
    - Windows: -Djava.library.path="lib/jnotify;lib/jintellitype"
    - Linux: -Djava.library.path="lib/jnotify:lib/jxgrabkey"
    - macOS: -Djava.library.path="lib/jnotify" -XstartOnFirstThread
  - Main class: net.sourceforge.docfetcher.gui.Application
  - For UI translations:
    - Click 'Modify options', then 'Modify classpath'.
    - Add the "dist/lang" folder to the classpath.
  Finally, select: Run > Run 'DocFetcher'


#===============================================================================
#	Building DocFetcher from the terminal
#===============================================================================

- Requirements:
  - Python 3.12 or newer
  - JDK 21 or newer; the bin folder of your JDK installation containing the
    various JDK binaries (javac, java, jar) must be on your PATH variable
  - Rust; the "cargo" executable must on the PATH
  - On Linux, MUSL must be installed to produce fully static Rust executables
    for Linux. You can install MUSL with the following command:
    rustup target add x86_64-unknown-linux-musl
  - On Linux, osxcross must be installed to cross-compile the Rust subproject to
    macOS. See the osxcross section further below for installation instructions.
- current-version.txt:
  - contains the version number used by all build scripts below
  - must not contain any extra whitespace or newlines
- build.py:
  - the build script that builds DocFetcher
  - output is in the "build" folder
  - imports various functions from build_lib.py
- build-jre.txt:
  - defines three "key=value" mappings, with the keys being jre_windows,
    jre_linux and jre_macos
  - read by build.py to locate the JREs that will be bundled with the release
    builds 
  - the values for jre_windows and jre_linux should point to folders containing
    the JRE's "bin" folder, while the value for jre_macos should point to the
    folder containing the JRE's "Contents" folder
- build-win-installer.nsi
  - NSIS script for building the Windows installer
  - requires NSIS and must be run on Windows
  - requires NSIS plugins in dev/nsis-dependencies;
    see installation instructions at the top of the .nsi file
  - must run build.py first before running this
  - output is in the "build" folder
- build-dmg.sh:
  - builds a macOS disk image
  - must run build.py first
  - must be run on Linux
  - requires program mkfs.hfsplus (try package hfsprogs on Ubuntu)
  - output is in the "build" folder
- mill:
  - In addition to the build.py script, the main Java codebase can be compiled
    with Mill, like so:
    - Incremental compilation: ./mill compile
    - Clean and compile: ./mill clean && ./mill compile
  - The Mill build system does not produce release artifacts like build.py, it
    merely compiles the codebase. The purpose is to complement the long-running
    Python build script with incremental compilation, to quickly check that new
    code changes don't break anything.
- code signing and notarization (optional):
  - code-signing-windows.txt:
    - for code signing on Windows
    - should contain SHA1 thumbprint of code signing certificate
    - if present, build.py and build-win-installer.nsi will perform code signing
    - the Windows SDK must be installed, and signtool.exe must be on the PATH
  - code-signing-macos.txt:
    - for code signing and notarization on macOS
    - should contain three "key=value" attributes:
      - certificate_file: path to certificate file in p12 format
      - password_file: path to file containing certificate password
      - api_key_file: path to JSON file containing App Store Connect API
        credentials:
        - issuer_id
        - key_id
        - private_key 
  - dmg-notarize.py
    - used for signing and notarizing all DMG files found in the "build" folder
    - must run build.py and build-dmg.py first


#===============================================================================
#	Cross-compilation of Rust subprojects from Linux to macOS with osxcross
#===============================================================================

The instructions in this section are based on the following article and were
tested on Ubuntu 24.04.
https://wapl.es/rust/2019/02/17/rust-cross-compile-linux-to-macos.html

# Install osxcross dependencies:
# The following packages were needed for an older version of osxcross. For the
# dependencies of the latest osxcross version, see the installation notes at:
# https://github.com/tpoechtrager/osxcross
sudo apt update
sudo apt install clang gcc g++ zlib1g-dev libmpc-dev libmpfr-dev libgmp-dev
sudo apt install cmake libxml2-dev libssl-dev

# Build failure on NTFS partitions:
For some reason, the ./build.sh command below will fail if osxcross is placed on
an NTFS partition. Use an ext4 partition instead.

# Build osxcross.
# Note: Choose a permanent location for osxcross beforehand, the executables
# will stop working if the location is changed later:
cd /path/to/parentdir/of/osxcross
git clone https://github.com/tpoechtrager/osxcross
# Optionally add a date string to new "osxcross" folder to indicate when the Git
# repository was cloned, e.g., "20250101":
mv osxcross osxcross_20250101
cd osxcross_20250101
wget -nc https://github.com/joseluisq/macosx-sdks/releases/download/10.12/MacOSX10.12.sdk.tar.xz
mv MacOSX10.12.sdk.tar.xz tarballs/
UNATTENDED=yes OSX_VERSION_MIN=10.12 ./build.sh

# Add osxcross to the PATH variable:
nano ~/.profile
# Append a line like the following one at the end of the .profile file:
export PATH="/path/to/parentdir/osxcross_20250101/target/bin:$PATH"
# Reload .profile file ("~" cannot be used):
cd
source .profile

# Add the macOS target to Rust:
rustup target add x86_64-apple-darwin

# The file "subprojects/.cargo/config.toml" is needed to tell Rust to use the
# osxcross executables to cross-compile to macOS.
