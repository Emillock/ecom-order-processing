$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk21, 'Machine')
$sysPath = [Environment]::GetEnvironmentVariable('PATH', 'Machine')
$parts = $sysPath -split ';' | Where-Object { $_ -notmatch 'jdk-1[79]\\bin|jdk-11.*\\bin|jre1\.8.*\\bin' -and $_ -ne '' }
$newPath = ($jdk21 + '\bin') + ';' + ($parts -join ';')
[Environment]::SetEnvironmentVariable('PATH', $newPath, 'Machine')
Write-Host "JAVA_HOME set to $jdk21"
Write-Host "System PATH updated"
