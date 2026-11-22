$ErrorActionPreference = 'Stop'
$salvageMod = Split-Path $PSScriptRoot -Parent
$salvageCore = [IO.Path]::GetFullPath((Join-Path $salvageMod '../../starsector-core'))
$salvageOutput = Join-Path ([IO.Path]::GetTempPath()) ('exile-salvage-check-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $salvageOutput | Out-Null
$salvageClasspath = "$salvageCore/*"
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin/javac.exe' --release 17 -cp $salvageClasspath -d $salvageOutput (Join-Path $PSScriptRoot 'ExileRefugeeSalvageCheck.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $salvageMod '../../jre/bin/java.exe') -cp "$salvageOutput;$salvageClasspath" ExileRefugeeSalvageCheck $salvageMod
exit $LASTEXITCODE
