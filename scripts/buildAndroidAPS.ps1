(get-host).ui.rawui.backgroundcolor = "black"
(get-host).ui.rawui.foregroundcolor = "green"

(get-host).ui.rawui.WindowTitle = "Build AndroidAPS"
Set-ExecutionPolicy Bypass
$ErrorActionPreference = "SilentlyContinue"


function Get-ScriptDirectory {
    $Invocation = (Get-Variable MyInvocation -Scope 1).Value;
    if($Invocation.PSScriptRoot)
    {
        $Invocation.PSScriptRoot;
    }
    Elseif($Invocation.MyCommand.Path)
    {
        Split-Path $Invocation.MyCommand.Path
    }
    else
    {
        $Invocation.InvocationName.Substring(0,$Invocation.InvocationName.LastIndexOf("\"));
    }
}

$scriptroot = Get-ScriptDirectory 
$script = "$scriptroot\buildAndroidAPS.ps1"
function StartScript {Invoke-Expression "$script"}
$apkFolder = "$scriptroot\..\app\build\outputs\apk"
$gradlewPath = "$scriptroot\..\gradlew.bat"

###############Menu functions########################
function DrawMenu {
	## supportfunction to the Menu function below
	param ($menuItems, $menuPosition, $menuTitel)
	$fcolor = "Green"
	$bcolor = "Black"
	$l = $menuItems.length + 1
	cls
	$menuwidth = $menuTitel.length + 4
	Write-Host -NoNewLine
	Write-Host ("*" * $menuwidth) -fore $fcolor -back $bcolor
	Write-Host -NoNewLine
	Write-Host "* $menuTitel *" -fore $fcolor -back $bcolor
	Write-Host -NoNewLine
	Write-Host ("*" * $menuwidth) -fore $fcolor -back $bcolor
	Write-Host ""
	Write-debug "L: $l MenuItems: $menuItems MenuPosition: $menuposition"
	for ($i = 0; $i -le $l;$i++) {
		Write-Host -NoNewLine
		if ($i -eq $menuPosition) {
			Write-Host "$($menuItems[$i])" -fore $bcolor -back $fcolor
		} 
		else {
			Write-Host "$($menuItems[$i])" -fore $fcolor -back $bcolor
		}
	}
}

function Menu {
    ## Generate a small "DOS-like" menu.
    ## Choose a menuitem using up and down arrows, select by pressing ENTER
    param ([array]$menuItems, $menuTitel = "MENU")
    $vkeycode = 0
    $pos = 0
    DrawMenu $menuItems $pos $menuTitel
    While ($vkeycode -ne 13) {
        $press = $host.ui.rawui.readkey("NoEcho,IncludeKeyDown")
        $vkeycode = $press.virtualkeycode
        Write-host "$($press.character)" -NoNewLine
        If ($vkeycode -eq 38) {$pos--}
        If ($vkeycode -eq 40) {$pos++}
        if ($pos -lt 0) {$pos = $menuItems.length -1}
		if ($pos -ge $menuItems.length) {$pos = 0}
        DrawMenu $menuItems $pos $menuTitel
    }
    Write-Output $($menuItems[$pos])
}
###############Menus and submenus########################

function MainMenu {
$options = "Install Jdk","Install Android SDK to $Env:USERPROFILE\AppData\Local\Android\Sdk","Install Android Studio (Optional)","Install Git","Switch to master Branch","Switch to dev Branch","--Build AAPS--","Full","NSClient","Openloop","Pumpcontrol","-Exit-"
	$selection = Menu $options "Build AndroidAPS"
	Switch ($selection) {
		"Install Jdk" {.$scriptroot\installJdk.ps1;anykey;MainMenu}
		"Install Android SDK to $Env:USERPROFILE\AppData\Local\Android\Sdk" {.$scriptroot\installAndroidSDK.ps1;anykey;MainMenu}
		"Install Android Studio (Optional)" {.$scriptroot\installAndroidStudio.ps1;anykey;MainMenu}
		"Install Git" {.$scriptroot\installGit.ps1;anykey;MainMenu}
		"Switch to master Branch" {
		git --git-dir=$scriptroot\..\.git --work-tree=$scriptroot\..\ fetch
		git --git-dir=$scriptroot\..\.git --work-tree=$scriptroot\..\ reset --hard MilosKozak/master
		anykey;MainMenu}
		"Switch to dev Branch" {
		git --git-dir=$scriptroot\..\.git --work-tree=$scriptroot\..\ fetch
		git --git-dir=$scriptroot\..\.git --work-tree=$scriptroot\..\ reset --hard MilosKozak/dev
		anykey;MainMenu}
		"--Build AAPS--" {MainMenu}
		"Full" {$flavor = "Full";assembly;anykey;MainMenu}
		"NSClient" {$flavor = "NSClient";assembly;anykey;MainMenu}
		"Openloop" {$flavor = "Openloop";assembly;anykey;MainMenu}
		"Pumpcontrol" {$flavor = "Pumpcontrol";assembly;anykey;MainMenu}
		"-Exit-" {Exit}
	}
}

function assembly {
$options = "Nowear","Wear","Wearcontrol","-Main Menu-","-Exit-"
	$selection = Menu $options "Select Wear Options!"
	Switch ($selection) {
		"Nowear" {
		cmd.exe /c start /wait $gradlewPath assemble"$flavor"Nowear
		cmd.exe /C $gradlewPath --stop
		if (Test-Path $apkFolder) { explorer $apkFolder}}
		"Wear" {
		cmd.exe /c start $gradlewPath assemble"$flavor"Wear
		cmd.exe /c $gradlewPath --stop 
		if (Test-Path $apkFolder) { explorer $apkFolder}}
		"Wearcontrol" {
		cmd.exe /c start $gradlewPath assemble"$flavor"Wearcontrolear
		cmd.exe /c $gradlewPath --stop 		
		if (Test-Path $apkFolder) { explorer $apkFolder}}
		"-Main Menu-" {return}
		"-Exit-" {Exit}
	}
}

function anykey {
Write-Host "Press Any Key To Continue... " 
$x = $host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

#call MainMenu
MainMenu



