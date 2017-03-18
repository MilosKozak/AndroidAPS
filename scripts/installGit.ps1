(get-host).ui.rawui.backgroundcolor = "black"
(get-host).ui.rawui.foregroundcolor = "green"
(get-host).ui.rawui.WindowTitle = "Install Android SDK"
$downloadFolder="$Env:USERPROFILE\Downloads"

$url = "https://github.com/git-for-windows/git/releases/download/v2.12.0.windows.1/Git-2.12.0-64-bit.exe"
$output = "$downloadFolder\Git.exe"

write-host "Downloading Git"
$client = new-object System.Net.WebClient
$client.DownloadFile($url,$output)
write-host "Installing Git"
Start-Process "$downloadFolder\Git.exe" -ArgumentList "/VERYSILENT" -wait -nonewwindow

If (Test-Path $output){			
		Remove-Item $output
	}

write-host "Finished install"