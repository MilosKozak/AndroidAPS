(get-host).ui.rawui.backgroundcolor = "black"
(get-host).ui.rawui.foregroundcolor = "green"
(get-host).ui.rawui.WindowTitle = "Install Android SDK"
$downloadFolder="$Env:USERPROFILE\Downloads"
$JDK_VER="8u121"
$JDK_FULL_VER="8u121-b13"
$JDK_PATH="1.8.0_121"
$id = "e9e7ea248e2c4826b92b3f075a80e441"
#http://download.oracle.com/otn-pub/java/jdk/8u121-b13/e9e7ea248e2c4826b92b3f075a80e441/jdk-8u121-windows-x64.exe
$source86 = "http://download.oracle.com/otn-pub/java/jdk/$JDK_FULL_VER/$id/jdk-$JDK_VER-windows-i586.exe"
$source64 = "http://download.oracle.com/otn-pub/java/jdk/$JDK_FULL_VER/$id/jdk-$JDK_VER-windows-x64.exe"
$destination86 = "$downloadFolder\$JDK_VER-x86.exe"
$destination64 = "$downloadFolder\$JDK_VER-x64.exe"
$client = new-object System.Net.WebClient
$cookie = "oraclelicense=accept-securebackup-cookie"
$client.Headers.Add([System.Net.HttpRequestHeader]::Cookie, $cookie)
 
Write-Host "Downloading x86 to $destination86"
$client.downloadFile($source86, $destination86)
if (!(Test-Path $destination86)) {
    Write-Host "Downloading $destination86 failed"
    Exit
}

Write-Host "Downloading x64 to $destination64"
$client.downloadFile($source64, $destination64)
if (!(Test-Path $destination64)) {
    Write-Host "Downloading $destination64 failed"
    Exit
}
 
try {
    Write-Host 'Installing JDK-x64'
    $proc1 = Start-Process -FilePath "$destination64" -ArgumentList "/s REBOOT=ReallySuppress" -Wait -PassThru
    $proc1.waitForExit()
    Write-Host 'Installation Done.'
 
    Write-Host 'Installing JDK-x86'
    $proc2 = Start-Process -FilePath "$destination86" -ArgumentList "/s REBOOT=ReallySuppress" -Wait -PassThru
    $proc2.waitForExit()
    Write-Host 'Installtion Done.'
} catch [exception] {
    write-host '$_ is' $_
    write-host '$_.GetType().FullName is' $_.GetType().FullName
    write-host '$_.Exception is' $_.Exception
    write-host '$_.Exception.GetType().FullName is' $_.Exception.GetType().FullName
    write-host '$_.Exception.Message is' $_.Exception.Message
}
 
if ((Test-Path "c:\Program Files (x86)\Java") -Or (Test-Path "c:\Program Files\Java")) {
    Write-Host 'Java installed successfully.'
}
Write-Host 'Setting up Path variables.'
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "c:\Program Files (x86)\Java\jdk$JDK_PATH", "Machine")
[System.Environment]::SetEnvironmentVariable("PATH", $Env:Path + ";c:\Program Files (x86)\Java\jdk$JDK_PATH\bin", "Machine")

If (Test-Path $destination64){			
		Remove-Item $destination64
	}
	
If (Test-Path $destination86){			
		Remove-Item $destination86
	}
	
Write-Host 'Done. Goodbye.'