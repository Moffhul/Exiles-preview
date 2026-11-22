$ErrorActionPreference = 'Stop'
$compatMod = Split-Path $PSScriptRoot -Parent
$compatGame = [IO.Path]::GetFullPath((Join-Path $compatMod '../..'))
$compatOutput = Join-Path ([IO.Path]::GetTempPath()) ('exile-vulture-check-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $compatOutput | Out-Null
$compatClasspath = "$compatGame/starsector-core/*;$compatGame/mods/Nexerelin/jars/*;$compatGame/mods/LazyLib/jars/*"
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin/javac.exe' --release 17 -cp $compatClasspath -d $compatOutput (Join-Path $PSScriptRoot 'ExileRefugeeSalvageCheck.java') (Join-Path $PSScriptRoot 'ExileVultureCompatibilityCheck.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $compatGame 'jre/bin/java.exe') -cp "$compatOutput;$compatClasspath" ExileVultureCompatibilityCheck $compatMod
exit $LASTEXITCODE
