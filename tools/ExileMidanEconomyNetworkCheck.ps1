$ErrorActionPreference = 'Stop'
$networkMod = Split-Path $PSScriptRoot -Parent
$networkCore = [IO.Path]::GetFullPath((Join-Path $networkMod '../../starsector-core'))
$networkOutput = Join-Path ([IO.Path]::GetTempPath()) ('exile-network-check-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $networkOutput | Out-Null
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin/javac.exe' --release 17 `
    -cp "$networkCore/*" -d $networkOutput `
    (Join-Path $networkMod 'data/scripts/exiles/ExileMidanEconomyNetwork.java') `
    (Join-Path $PSScriptRoot 'ExileMidanEconomyNetworkCheck.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $networkMod '../../jre/bin/java.exe') -cp "$networkOutput;$networkCore/*" `
    data.scripts.exiles.ExileMidanEconomyNetworkCheck
exit $LASTEXITCODE
