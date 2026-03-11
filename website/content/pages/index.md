Title: {{ PAGE_TITLE_OVERVIEW }}
Slug: index
Lang: en
url:
save_as: index.html
Keywords: desktop search, file management, search, document, retrieval, indexing
Description: Homepage of DocFetcher, a desktop search application for fast document retrieval

<div class="note" markdown="1">
**Note**: You may be interested in DocFetcher Pro, the commercial big brother of DocFetcher with more features, or DocFetcher Server, the commercial cousin of DocFetcher with multi-user support and a web interface. [Find out more](https://docfetcherpro.com/).
</div>

Description
===========
DocFetcher is an open-source desktop search application: It allows you to search the contents of files on your computer. &mdash; You can think of it as Google for your local files. The application runs on Windows, Linux and macOS, and is made available under the [Eclipse Public License](https://en.wikipedia.org/wiki/Eclipse_Public_License).

Basic Usage
===========
The screenshot below shows the main user interface. Queries are entered in the text field at (1). The search results are displayed in the result pane at (2). The preview pane at (3) shows a text-only preview of the file currently selected in the result pane. All matches in the file are highlighted in yellow.

You can filter the results by minimum and/or maximum filesize (4), by file type (5) and by location (6). The buttons at (7) are used for opening the manual, opening the preferences and minimizing the program into the system tray, respectively.

<div class="img-container" style="text-align: center;"><a href="/theme/intro-001-results-edited.png"><img src="/theme/intro-001-results-edited.png"></a></div>

DocFetcher requires that you create so-called *indexes* for the folders you want to search in. What indexing is and how it works is explained in more detail below. In a nutshell, an index allows DocFetcher to find out very quickly (in the order of milliseconds) which files contain a particular set of words, thereby vastly speeding up searches. The following screenshot shows DocFetcher's dialog for creating new indexes:

<div class="img-container" style="text-align: center;"><a href="/theme/intro-002-config.png"><img src="/theme/intro-002-config.png"></a></div>

Clicking the "Run" button on the bottom right of this dialog starts the indexing. The indexing process can take a while, depending on the number and sizes of the files to be indexed. A good rule of thumb is 200 files per minute.

While creating an index takes time, it has to be done only once per folder. Also, *updating* an index after the folder's contents have changed is much faster than creating it &mdash; it usually takes only a couple of seconds.

Notable Features
================
* **Portable versions**: There are portable versions of DocFetcher that run on Windows, Linux and macOS, respectively. These portable versions allow you to create a *portable document repository*: a fully indexed and fully searchable repository of all your important documents that you can freely move around. This means you can carry it with you on a USB drive, package it for archival purposes, put it in an encrypted volume, synchronize it between multiple computers via a cloud drive, or even upload it and share it with the rest of the world.
* **Unicode support**: DocFetcher comes with rock-solid Unicode support for all major formats, including Microsoft Office, OpenOffice.org, PDF, HTML, RTF and plain text files.
* **Archive support**: DocFetcher supports the following archive formats: zip, 7z, rar, and the whole tar.* family. The file extensions for zip archives can be customized, allowing you to add more zip-based archive formats as needed. Also, DocFetcher can handle an unlimited nesting of archives (e.g., a zip archive containing a 7z archive containing a rar archive... and so on).
* **Search in source code files**: The file extensions by which DocFetcher recognizes plain text files can be customized, so you can use DocFetcher for searching in any kind of source code and other text-based file formats. (This works quite well in combination with the customizable zip extensions, e.g., for searching in Java source code inside Jar files.)
* **Outlook PST files**: DocFetcher allows searching for Outlook emails, which Microsoft Outlook typically stores in PST files.
* **Detection of HTML pairs**: By default, DocFetcher detects pairs of HTML files (e.g., a file named "foo.html" and a folder named "foo_files"), and treats the pair as a single document. This feature may seem rather useless at first, but it turned out that this dramatically increases the quality of the search results when you're dealing with HTML files, since all the "clutter" inside the HTML folders disappears from the results.
* **Regex-based exclusion of files from indexing**: You can use regular expressions to exclude certain files from indexing. For example, to exclude Microsoft Excel files, you can use a regular expression like this: `.*\.xls`
* **Mime-type detection**: You can use regular expressions to turn on "mime-type detection" for certain files, meaning that DocFetcher will try to detect their actual file types not just by looking at the filename, but also by peeking into the file contents. This comes in handy for files that have the wrong file extension.
* **Powerful query syntax**: In addition to basic constructs like `OR`, `AND` and `NOT` DocFetcher also supports, among other things: Wildcards, phrase search, fuzzy search ("find words that are similar to..."), proximity search ("these two words should be at most 10 words away from each other"), boosting ("increase the score of documents containing...")

Supported Document Formats
==========================
* Microsoft Office (doc, xls, ppt)
* Microsoft Office 2007 and newer (docx, xlsx, pptx, docm, xlsm, pptm)
* Microsoft Outlook (pst)
* OpenOffice.org (odt, ods, odg, odp, ott, ots, otg, otp)
* Portable Document Format (pdf)
* EPUB (epub)
* HTML (html, xhtml, ...)
* TXT and other plain text formats (customizable)
* Rich Text Format (rtf)
* AbiWord (abw, abw.gz, zabw)
* Microsoft Compiled HTML Help (chm)
* MP3 Metadata (mp3)
* FLAC Metadata (flac)
* JPEG Exif Metadata (jpg, jpeg)
* Microsoft Visio (vsd)
* Scalable Vector Graphics (svg)

Design Philosophy
=================
DocFetcher's design follows these principles:

**Crap-free**: DocFetcher's user interface is designed to be clutter- and crap-free. No useless stuff is installed in your system.

**Privacy**: DocFetcher does not collect your private data, period. Feel free to check the [source code](https://sourceforge.net/p/docfetcher/wiki/Source%20code/).

**Indexing only what you need**: Other search software indexes your entire hard drive *by default* &mdash; perhaps to accommodate "dumb" users, by taking decisions away from them, or to harvest more user data. DocFetcher on the other hand indexes *nothing* by default, leaving the selection of data to be indexed to users. This is based on the observation that indexing entire hard drives is usually an enormous waste of indexing time and disk space that also leads to a cluttering of search results with irrelevant files.

How Indexing Works
==================
This section explains what indexing is and how it works.

**The naive approach to file search**: The most basic approach to file search is to simply visit every file in a certain location one-by-one whenever a search is performed. This works well enough for *filename-only* search, because analyzing filenames is very fast. However, it wouldn't work so well if you wanted to search the *contents* of files, since full text extraction is a much more expensive operation than filename analysis.

**Index-based search**: That's why DocFetcher, being a content searcher, takes an approach known as *indexing*: The basic idea is that most of the files people need to search (like, more than 95%) are modified very infrequently or not at all. So, rather than doing full text extraction on every file on every search, it is far more efficient to perform text extraction on all files just *once*, and to create a so-called *index* from all the extracted text. This index is kind of like a dictionary that allows quickly looking up files by the words they contain.

**Telephone book analogy**: As an analogy, consider how much more efficient it is to look up someone's phone number in a telephone book (the "index"), compared to calling *every* possible phone number just to find out whether the person on the other end is the one you're looking for. &mdash; Calling someone over the phone and extracting text from a file can both be considered "expensive operations". Also, the fact that people don't change their phone numbers frequently is analogous to the fact that most files on a computer are rarely if ever modified.

**Index updates**: Of course, an index only reflects the state of the indexed files when it was created, not necessarily the latest state of the files. Thus, if the index isn't kept up-to-date, you could get outdated search results, much in the same way a telephone book can become out of date. However, this shouldn't be much of a problem if we can assume that most of the files are rarely modified. Additionally, DocFetcher is capable of *automatically* updating its indexes: (1) When it's running, it detects changed files and updates its indexes accordingly. (2) When it *isn't* running, a small daemon in the background will detect changes and keep a list of indexes to be updated; DocFetcher will then update those indexes the next time it is started.
