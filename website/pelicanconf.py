#!/usr/bin/env python3

from datetime import datetime

# Basic site settings
AUTHOR = 'Nam-Quang Tran'
SITENAME = 'DocFetcher – Fast Document Search'
SITEURL = ""

# Content settings
PATH = "content"
DEFAULT_LANG = 'en'
TIMEZONE = 'UTC'

# Load app version from definition in parent folder
with open("../current-version.txt") as f:
    APP_VERSION = f.read().strip()

SF_BASE_URL = 'https://sourceforge.net/projects/docfetcher/files/docfetcher'

# Plugins
PLUGINS = ['i18n_subsites', 'jinja2content']

# Theme
THEME = 'themes/docfetcher'

# Page and article configuration
PAGE_URL = '{slug}/'
PAGE_SAVE_AS = '{slug}/index.html'
ARTICLE_PATHS = []  # Disable articles (we're only using pages)
DIRECT_TEMPLATES = []  # Disable index, tags, categories, archives pages
AUTHOR_SAVE_AS = ''
AUTHORS_SAVE_AS = ''
CATEGORY_SAVE_AS = ''
CATEGORIES_SAVE_AS = ''
TAG_SAVE_AS = ''
TAGS_SAVE_AS = ''
ARCHIVES_SAVE_AS = ''

# Feed configuration
FEED_ALL_ATOM = None
CATEGORY_FEED_ATOM = None
TRANSLATION_FEED_ATOM = None
AUTHOR_FEED_ATOM = None
AUTHOR_FEED_RSS = None

# Template and Jinja settings
JINJA_ENVIRONMENT = {}
CURRENT_YEAR = datetime.now().year

# Other settings
DEFAULT_PAGINATION = False
RELATIVE_URLS = False

STATIC_PATHS = ['extra']

EXTRA_PATH_METADATA = {
    'extra/changelog.txt': {'path': 'changelog.txt'},
    'extra/doku.php': {'path': 'wiki/doku.php'}
}

# i18n configuration
I18N_SUBSITES = {
    'de': {
        'SITENAME': 'DocFetcher – Schnelle Dokument-Suche',
    },
    'es': {
        'SITENAME': 'DocFetcher – Búsqueda Rápida de Documentos',
    },
    'fr': {
        'SITENAME': 'DocFetcher – Recherche Rapide de Documents',
    },
    'it': {
        'SITENAME': 'DocFetcher – Ricerca Rapida di Documenti',
    },
    'ru': {
        'SITENAME': 'DocFetcher – Быстрый Поиск Документов',
    },
    'tr': {
        'SITENAME': 'DocFetcher – Hızlı Belge Arama',
    },
    'uk': {
        'SITENAME': 'DocFetcher – Швидкий Пошук Документів',
    },
    'zh': {
        'SITENAME': 'DocFetcher – 快速文档搜索',
    },
}

# Language configuration for templates
LANG_CODES = ['en'] + list(I18N_SUBSITES.keys())
LANG_URLS = {'en': '/'}
for lang_code in I18N_SUBSITES.keys():
    LANG_URLS[lang_code] = f'/{lang_code}/'

# Menu items and page titles
MENU_ITEMS = {
    'en': {
        'overview': 'Overview',
        'download': 'Download',
        'screenshots': 'Screenshots',
        'more': 'More',
    },
    'de': {
        'overview': 'Übersicht',
        'download': 'Download',
        'screenshots': 'Screenshots',
        'more': 'Mehr',
    },
    'es': {
        'overview': 'Resumen',
        'download': 'Download',
        'screenshots': 'Capturas de pantalla',
        'more': 'Más',
    },
    'fr': {
        'overview': 'Aperçu',
        'download': 'Télécharger',
        'screenshots': 'Captures d\'écran',
        'more': 'Plus',
    },
    'it': {
        'overview': 'Panoramica',
        'download': 'Download',
        'screenshots': 'Immagini',
        'more': 'Altro',
    },
    'ru': {
        'overview': 'Обзор',
        'download': 'Скачать',
        'screenshots': 'Скриншоты',
        'more': 'Больше',
    },
    'tr': {
        'overview': 'Genel bakış',
        'download': 'İndir',
        'screenshots': 'Ekran görüntüleri',
        'more': 'Daha fazla',
    },
    'uk': {
        'overview': 'Огляд',
        'download': 'Завантаження',
        'screenshots': 'Екранознімки',
        'more': 'Більше',
    },
    'zh': {
        'overview': '概述',
        'download': '下载',
        'screenshots': '截图',
        'more': '更多',
    },
}

# Jinja2 configuration
JINJA_GLOBALS = {
    'APP_VERSION': APP_VERSION,
    'DOWNLOAD_WINDOWS_NONPORTABLE':
        f'{SF_BASE_URL}/{APP_VERSION}/DocFetcher-{APP_VERSION}-Windows-64bit-Setup.exe/download',
    'DOWNLOAD_WINDOWS_PORTABLE':
        f'{SF_BASE_URL}/{APP_VERSION}/DocFetcher-{APP_VERSION}-Windows-64bit-Portable.zip/download',
    'DOWNLOAD_LINUX_NONPORTABLE':
        f'{SF_BASE_URL}/{APP_VERSION}/DocFetcher-{APP_VERSION}-Linux-64bit-NonPortable.zip/download',
    'DOWNLOAD_LINUX_PORTABLE':
        f'{SF_BASE_URL}/{APP_VERSION}/DocFetcher-{APP_VERSION}-Linux-64bit-Portable.zip/download',
    'DOWNLOAD_MACOS_NONPORTABLE':
        f'{SF_BASE_URL}/{APP_VERSION}/DocFetcher-{APP_VERSION}-macOS-64bit-NonPortable.dmg/download',
    'DOWNLOAD_MACOS_PORTABLE':
        f'{SF_BASE_URL}/{APP_VERSION}/DocFetcher-{APP_VERSION}-macOS-64bit-Portable.dmg/download',
    'PAGE_TITLE_OVERVIEW': SITENAME,
    'PAGE_TITLE_DOWNLOAD': MENU_ITEMS['en']['download'],
    'PAGE_TITLE_SCREENSHOTS': MENU_ITEMS['en']['screenshots'],
    'PAGE_TITLE_MORE': MENU_ITEMS['en']['more'],
    'LANG_CODES': LANG_CODES,
    'LANG_URLS': LANG_URLS
}

# Add page title variables to JINJA_GLOBALS for each non-English language
for lang in I18N_SUBSITES.keys():
    if 'JINJA_GLOBALS' not in I18N_SUBSITES[lang]:
        I18N_SUBSITES[lang]['JINJA_GLOBALS'] = {}
    globals_dict = I18N_SUBSITES[lang]['JINJA_GLOBALS']
    globals_dict['PAGE_TITLE_OVERVIEW'] = I18N_SUBSITES[lang]['SITENAME']
    globals_dict['PAGE_TITLE_DOWNLOAD'] = MENU_ITEMS[lang]['download']
    globals_dict['PAGE_TITLE_SCREENSHOTS'] = MENU_ITEMS[lang]['screenshots']
    globals_dict['PAGE_TITLE_MORE'] = MENU_ITEMS[lang]['more']
