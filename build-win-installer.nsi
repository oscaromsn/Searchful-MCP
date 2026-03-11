; NSIS script for building the Windows installer
;
; This script must be run after running build.py, because it expects to find the
; DocFetcher jar in the build folder.
;
; DEPENDENCIES
; All dependencies of this script can be found in dev/nsis-dependencies, BUT
; those files might be out of date, so try the latest versions of the
; dependencies first, if any are available. They can be found here:
; - https://nsis.sourceforge.io/ApplicationID_plug-in
; - https://nsis.sourceforge.io/Processes_plug-in
; In case of errors, try updating NSIS.
;
; IMPORTANT NOTE ABOUT THE UNINSTALLER
; The uninstaller section below unconditionally removes the app data folder,
; which is the expected behavior when users manually uninstall the application.
; However, this means the uninstaller should NEVER be invoked programmatically
; during an upgrade, as doing so would wipe out the user's indexes and settings.
; Instead, manual cleanup of only the program files and registry entries must be
; performed.

RequestExecutionLevel admin ; without this, the startmenu links won't be removed on Windows Vista and later
Unicode false
SetCompress force
SetCompressor /FINAL zlib

!define /file VERSION "current-version.txt"
!define NON_PORTABLE_PATH build\DocFetcher-${VERSION}-Windows-64bit-NonPortable

Name "DocFetcher ${VERSION}"
XPStyle on
OutFile build\DocFetcher-${VERSION}-Windows-64bit-Setup.exe

; Read certificate thumbprint from configuration file and automatically sign
; installer and uninstaller. If the certificate file is missing or empty, the
; installer and uninstaller will be left unsigned.
!if /fileexists "code-signing-windows.txt"
	!define /file CERT_THUMBPRINT "code-signing-windows.txt"
	!if "${CERT_THUMBPRINT}" != ""
		!finalize 'signtool.exe sign /sha1 "${CERT_THUMBPRINT}" /tr "http://time.certum.pl" /td sha256 /fd sha256 /v "%1"'
		!uninstfinalize 'signtool.exe sign /sha1 "${CERT_THUMBPRINT}" /tr "http://time.certum.pl" /td sha256 /fd sha256 /v "%1"'
	!endif
!endif

InstallDir $PROGRAMFILES64\DocFetcher
Page directory
Page instfiles
Page custom finalPage
UninstPage uninstConfirm
UninstPage instfiles

AllowSkipFiles off
ShowInstDetails show
ShowUninstDetails show
AutoCloseWindow true

!addplugindir dev
!include "FileFunc.nsh"
!include "nsDialogs.nsh"
!insertmacro GetTime

; Follow the list of languages on the wiki:
; http://sourceforge.net/apps/mediawiki/docfetcher/index.php?title=How_to_translate_DocFetcher#Translations_that_are_already_finished_or_in_progress
LoadLanguageFile "${NSISDIR}\Contrib\Language files\English.nlf"
LoadLanguageFile "${NSISDIR}\Contrib\Language files\German.nlf"
LoadLanguageFile "${NSISDIR}\Contrib\Language files\French.nlf"
LoadLanguageFile "${NSISDIR}\Contrib\Language files\Portuguese.nlf"
LoadLanguageFile "${NSISDIR}\Contrib\Language files\Romanian.nlf"
LoadLanguageFile "${NSISDIR}\Contrib\Language files\Spanish.nlf"
; These languages require enabling Unicode support:
; LoadLanguageFile "${NSISDIR}\Contrib\Language files\SimpChinese.nlf"
; LoadLanguageFile "${NSISDIR}\Contrib\Language files\TradChinese.nlf"
; LoadLanguageFile "${NSISDIR}\Contrib\Language files\Greek.nlf"
; LoadLanguageFile "${NSISDIR}\Contrib\Language files\Japanese.nlf"
; LoadLanguageFile "${NSISDIR}\Contrib\Language files\Russian.nlf"

Function .onInit
	startinst:
	Processes::FindProcess "DocFetcher.exe"
	StrCmp $R0 0 done
	MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION \
		"DocFetcher is running! Please close it before reinstalling." \
	IDRETRY startinst
	Abort
	done:

	; Check for and clean up old 32-bit version (without removing app data)
	ifFileExists "$PROGRAMFILES\DocFetcher\uninstaller.exe" 0 skip32bitcleanup
		DetailPrint "Removing previous 32-bit installation..."
		SetShellVarContext all
		RMDir /r "$PROGRAMFILES\DocFetcher"
		DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\DocFetcher"
		DeleteRegValue HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "DocFetcher-Daemon"
	skip32bitcleanup:
FunctionEnd

Var CHECKBOX
Var boolCHECKBOX
Var Image
Var ImageHandle

; --------------------------------
; The final install page that asks to run the application
Function finalPage
	IfRebootFlag 0 noreboot
		MessageBox MB_YESNO "A reboot is required to finish the installation.$\n$\n Do you wish to reboot now?" IDNO endfinalpage
			Reboot
	noreboot:
	nsDialogs::Create 1018
	Pop $0
	${NSD_CreateLabel} 75u 30u 80% 8u "DocFetcher was succesfully installed on your computer."
	Pop $0
	${NSD_CreateCheckbox} 80u 50u 50% 8u "Run DocFetcher ${VERSION}"
	Pop $CHECKBOX
	SendMessage $CHECKBOX ${BM_SETCHECK} ${BST_CHECKED} 0
	GetFunctionAddress $1 OnCheckbox
	nsDialogs::OnClick $CHECKBOX $1
	
	; Add an image
	${NSD_CreateBitmap} 0 0 100% 40% ""
	Pop $Image
	${NSD_SetImage} $Image "$INSTDIR\img\setup.bmp" $ImageHandle
	nsDialogs::Show
	${NSD_freeImage} $ImageHandle
	endfinalpage:
FunctionEnd
Function OnCheckbox
	SendMessage $CHECKBOX ${BM_GETSTATE} 0 0 $1
	${If} $1 != 520
		StrCpy $boolCHECKBOX "True"
	${Else}
		StrCpy $boolCHECKBOX "False"
	${EndIf}
FunctionEnd
Function .onInstSuccess
	IfRebootFlag endpage 0
	${If} $boolCHECKBOX != "False"
		Exec "$INSTDIR\DocFetcher.exe"
	${EndIf}
	endpage:
FunctionEnd
Function .onInstFailed
	DetailPrint " --- "
	DetailPrint " Make sure DocFetcher is not running and try installing again."
	MessageBox MB_OK|MB_ICONEXCLAMATION "Please restart your computer and try the installation again."
FunctionEnd

Section "DocFetcher"
	SetShellVarContext all
	killdaemon:
		Processes::FindProcess "docfetcher-daemon-windows"
		StrCmp $R0 0 nodaemon
		DetailPrint "Attempting to kill DocFetcher daemon..."
		Processes::KillProcess "docfetcher-daemon-windows"
		Sleep 250
	Goto killdaemon
	nodaemon:
	
	; Remove the existing program folder. This is necessary because:
	; - Otherwise the uninstaller might not work cleanly.
	; - Loading different versions of the same library might crash the program. See bug #3558268.
	RMDir /r $INSTDIR
	
	; Copy files
	SetOutPath $INSTDIR
	File ${NON_PORTABLE_PATH}\*.exe
	File ${NON_PORTABLE_PATH}\*.bat
	File ${NON_PORTABLE_PATH}\*.txt
	File ${NON_PORTABLE_PATH}\*.py
	
	SetOutPath $INSTDIR\help
	File /r ${NON_PORTABLE_PATH}\help\*.*
	
	SetOutPath $INSTDIR\img
	File /r ${NON_PORTABLE_PATH}\img\*.*
	
	SetOutPath $INSTDIR\jre
	File /r ${NON_PORTABLE_PATH}\jre\*.*
	
	SetOutPath $INSTDIR\lang
	File /r ${NON_PORTABLE_PATH}\lang\*.*
	
	SetOutPath $INSTDIR\misc
	File /r ${NON_PORTABLE_PATH}\misc\*.*
	
	SetOutPath $INSTDIR\py4j
	File /r ${NON_PORTABLE_PATH}\py4j\*.*
	
	Delete /REBOOTOK "$INSTDIR\lib\net.sourceforge.docfetcher*.*"
	SetOutPath $INSTDIR\lib
	File /r /x *.so /x *.dylib /x *linux* /x *macosx* /x *docfetcher*.jar ${NON_PORTABLE_PATH}\lib\*.*
	File build\net.sourceforge.docfetcher-*-nonportable*.jar
	
	; Uninstaller
	WriteUninstaller $INSTDIR\uninstaller.exe
	
	; Write to registry
	Var /GLOBAL regkey
	Var /GLOBAL homepage
	StrCpy $regkey "Software\Microsoft\Windows\CurrentVersion\Uninstall\DocFetcher"
	StrCpy $homepage "https://docfetcher.sourceforge.io"
	WriteRegStr HKLM $regkey "DisplayName" "DocFetcher"
	WriteRegStr HKLM $regkey "UninstallString" "$INSTDIR\uninstaller.exe"
	WriteRegStr HKLM $regkey "InstallLocation" $INSTDIR
	WriteRegStr HKLM $regkey "DisplayIcon" "$INSTDIR\DocFetcher.exe,0"
	WriteRegStr HKLM $regkey "HelpLink" $homepage
	WriteRegStr HKLM $regkey "URLUpdateInfo" $homepage
	WriteRegStr HKLM $regkey "URLInfoAbout" $homepage
	WriteRegStr HKLM $regkey "DisplayVersion" "${VERSION}"
	WriteRegDWORD HKLM $regkey "NoModify" 1
	WriteRegDWORD HKLM $regkey "NoRepair" 1
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "DocFetcher-Daemon" "$INSTDIR\docfetcher-daemon-windows.exe"
	
	SetShellVarContext current
	
	; Start menu entries
	CreateDirectory $SMPROGRAMS\DocFetcher
	CreateShortCut $SMPROGRAMS\DocFetcher\DocFetcher.lnk $INSTDIR\DocFetcher.exe
	CreateShortCut "$SMPROGRAMS\DocFetcher\Uninstall DocFetcher.lnk" $INSTDIR\uninstaller.exe
	CreateShortCut $SMPROGRAMS\DocFetcher\Readme.lnk $INSTDIR\Readme.txt
	ApplicationID::Set "$SMPROGRAMS\DocFetcher\DocFetcher.lnk" "DocFetcher"
	
	; Launch daemon
	Exec '"$INSTDIR\docfetcher-daemon-windows.exe"'
SectionEnd

Section "un.Uninstall"
	SetShellVarContext all
	
	; Kill daemon
	Processes::KillProcess "docfetcher-daemon-windows"
	Sleep 1000
	
	; Remove program folder
	RMDir /r /REBOOTOK $INSTDIR
	
	; Remove registry key
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\DocFetcher"
	DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "DocFetcher-Daemon"
	
	SetShellVarContext current
	
	; Remove application data folder
	RMDir /r /REBOOTOK $APPDATA\DocFetcher
	
	; Remove start menu entries
	Delete /REBOOTOK $SMPROGRAMS\DocFetcher\DocFetcher.lnk
	Delete /REBOOTOK "$SMPROGRAMS\DocFetcher\Uninstall DocFetcher.lnk"
	Delete $SMPROGRAMS\DocFetcher\Readme.lnk
	RMDir $SMPROGRAMS\DocFetcher
SectionEnd
