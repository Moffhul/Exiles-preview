$ErrorActionPreference = 'Stop'
$debtMod = Split-Path $PSScriptRoot -Parent
$debtCore = [IO.Path]::GetFullPath((Join-Path $debtMod '../../starsector-core'))
$debtOutput = Join-Path ([IO.Path]::GetTempPath()) ('exile-debt-check-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $debtOutput | Out-Null
$debtSources = @(
    (Join-Path $debtMod 'src/data/scripts/rulecmd/ExileStartingFines.java'),
    (Join-Path $debtMod 'data/scripts/exiles/ExileStartingDebtLedger.java'),
    (Join-Path $debtMod 'data/scripts/exiles/ExileStartingDebtConfig.java'),
    (Join-Path $debtMod 'data/scripts/exiles/ExileStartingDebt.java'),
    (Join-Path $PSScriptRoot 'ExileStartingDebtCheck.java')
)
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin/javac.exe' --release 17 -cp "$debtCore/*" -d $debtOutput $debtSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $debtMod '../../jre/bin/java.exe') --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.desktop/java.awt.font=ALL-UNNAMED -cp "$debtOutput;$debtCore/*" ExileStartingDebtCheck $debtMod
exit $LASTEXITCODE
