#!/usr/bin/env python3

import os
import os.path as osp
import sys
from build_lib import (
    call, check_rcodesign_availability, parse_macos_signing_config,
    validate_macos_signing_config_files
)

def quit(msg):
    print(msg.strip())
    exit(1)

def _find_dmg_files(build_directory: str) -> list[str]:
    """Find all DMG files in the specified build directory."""
    if not osp.isdir(build_directory):
        quit(f"Build directory does not exist: {build_directory}")

    dmg_files = []
    for filename in os.listdir(build_directory):
        if filename.lower().endswith(".dmg"):
            dmg_files.append(osp.join(build_directory, filename))

    if not dmg_files:
        quit(
            f"No DMG files found in {build_directory}. "
            "Please run the appropriate build script first "
            "to generate DMG files."
        )

    return sorted(dmg_files)

def _notarize_dmg(dmg_path: str, config: dict[str, str]) -> None:
    """Notarize a single DMG file using rcodesign."""

    # Generate paths for unnotarized backup and notarized output
    base_name = osp.splitext(dmg_path)[0]
    ext = osp.splitext(dmg_path)[1]
    unnotarized_path = f"{base_name}_unnotarized{ext}"
    notarized_path = dmg_path  # Notarized version gets the original name

    # Rename original file to have _unnotarized suffix
    if osp.exists(unnotarized_path):
        os.remove(unnotarized_path)
    os.rename(dmg_path, unnotarized_path)

    # Sign the DMG
    ret = call(
        "rcodesign", "sign",
        "--p12-file", config["certificate_file"],
        "--p12-password-file", config["password_file"],
        unnotarized_path,
        notarized_path
    )

    if ret != 0:
        # Restore original file on failure
        os.rename(unnotarized_path, dmg_path)
        quit(f"Failed to sign DMG file: {dmg_path}")

    # Notarize and staple
    ret = call(
        "rcodesign", "notary-submit",
        "--api-key-path", config["api_key_file"],
        "--staple",
        notarized_path
    )

    if ret != 0:
        # Restore original file on failure
        os.remove(notarized_path)
        os.rename(unnotarized_path, dmg_path)
        quit(f"Failed to notarize DMG file: {notarized_path}")

def notarize_dmgs_in_directory(build_dir: str, config_file: str) -> None:
    """Notarize all DMG files in the specified directory."""
    check_rcodesign_availability()
    if not osp.isfile(config_file):
        quit(f"File not found: {config_file}")
    config = parse_macos_signing_config(config_file)
    validate_macos_signing_config_files(config_file, config)
    dmg_files = _find_dmg_files(build_dir)

    if any(x.lower().endswith("_unnotarized.dmg") for x in dmg_files):
        try:
            ans = input(
                "It seems the DMG files were already notarized. "
                "Continue anyway? [y/n] "
            )
            if ans.strip().lower() != "y":
                exit(0)
        except KeyboardInterrupt:
            print()
            exit(0)

    for dmg_file in dmg_files:
        print(f"Notarizing: {osp.basename(dmg_file)}")
        _notarize_dmg(dmg_file, config)

def main():
    os.chdir(osp.dirname(osp.abspath(sys.argv[0])))
    notarize_dmgs_in_directory("./build", "./code-signing-macos.txt")

if __name__ == "__main__":
    main()