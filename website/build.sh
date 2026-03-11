#!/usr/bin/env bash

# Build script for DocFetcher website using Pelican

set -e

rm -rf output

source .venv/bin/activate

pelican content -s pelicanconf.py
