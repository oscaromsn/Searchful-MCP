Erhöhung des Speicherlimits
===========================
Für DocFetcher besteht standardmäßig ein Speicherlimit von 256&nbsp;MB. Dies kann durch Modifikation der plattformspezifischen Startprogramme abgeändert werden.

Windows
-------
In der Windows-Version von DocFetcher sind vorgefertigte alternative Startprogramme mitgeliefert, welche andere Speicherlimits setzen. Diese alternativen Startprogramme sind auf folgende Weise zu benutzen:

* Öffnen Sie den DocFetcher-Ordner. Falls Sie die portable Version von DocFetcher verwenden, ist dies einfach der Ordner, den Sie heruntergeladen und entpackt haben. Falls Sie die nicht-portable Version benutzen, sollte sich DocFetcher in `C:\Program Files`, `C:\Program Files (x86)` oder an einem ähnlichen Ort befinden.
* Die alternativen Startprogramme befinden sich im Ordner `DocFetcher\misc`. Sie weisen alle einen Dateinamen `DocFetcher-XXX.exe` auf, wobei das `XXX` für das Speicherlimit des jeweiligen Startprogramms steht. Das Startprogramm `DocFetcher-512.exe` bspw. setzt ein Speicherlimit von 512&nbsp;MB.
* Vor Gebrauch eines der Startprogramme **müssen Sie es zuerst in den DocFetcher-Ordner darüber verschieben oder kopieren**. Es ist nicht notwendig, das normale Startprogramm zu löschen oder das alternative Startprogramm umzubenennen.

Ein weiterer Weg, das Speicherlimit zu ändern, besteht darin, die Datei `misc\DocFetcher.bat` in den DocFetcher-Ordner zu kopieren und den Ausdruck `-Xmx256m` in der letzten Zeile der Datei umzuändern, z.&nbsp;B. zu `-Xmx512m`.

Linux
-----
Öffnen Sie das Startskript `DocFetcher/DocFetcher.sh` mit einem Text-Editor und ändern Sie in der letzten Zeile den Ausdruck `-Xmx256m` entsprechend ab, bspw. zu `-Xmx512m`.

Mac OS&nbsp;X
-------------
Unter Mac OS&nbsp;X wird das Speicherlimit über eine Datei namens `memory-limit.txt` konfiguriert. Der Speicherort dieser Datei hängt von der verwendeten Version ab:

* **Portable Version**: Öffnen Sie im DocFetcher-Ordner die Datei `conf/memory-limit.txt` mit einem Text-Editor.
* **Nicht-portable Version**: Öffnen Sie in Ihrem Home-Verzeichnis die Datei `.docfetcher/conf/memory-limit.txt` mit einem Text-Editor. Ordner, die mit einem Punkt beginnen, sind im Finder standardmäßig ausgeblendet. Um versteckte Dateien anzuzeigen, drücken Sie `Cmd+Shift+.` (Befehl+Umschalt+Punkt) im Finder, oder verwenden Sie das Terminal, um zur Datei zu navigieren.

Falls die Datei noch nicht existiert, starten Sie DocFetcher einmal, um sie automatisch zu erstellen. Nach dem Öffnen der Datei:

* Sie sehen einen Wert wie `4g` unter einigen Kommentarzeilen. Ändern Sie diesen auf das gewünschte Speicherlimit, z.&nbsp;B. zu `8g` für 8&nbsp;GB RAM.
* Speichern und schließen Sie die Datei, schließen Sie dann das Programm, falls es noch geöffnet ist, und starten Sie das Programm neu.
