This folder contains the source files for the project website.

Python 3.12 or later must be installed. All dependencies are managed in the
.venv directory.

The source files are in the "content" subfolder. The website files compiled from
the source files are in the "output" subfolder.

How to compile and view the website:

# Create a Python virtual environment for Pelican:
python -m venv .venv

# Activate the virtual environment (needed for all commands below):
source .venv/bin/activate

# Install Pelican:
python -m pip install pelican

# Install required Pelican plugins:
python -m pip install "pelican[markdown]"
python -m pip install pelican-i18n-subsites
python -m pip install pelican-jinja2content

# Compile the website during development:
pelican content

# Start the web server to serve the compiled website files:
pelican --listen

# Compile the website in production:
pelican content -s publishconf.py