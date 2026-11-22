$ErrorActionPreference = 'Stop'
$startMod = Split-Path $PSScriptRoot -Parent
$startCore = [IO.Path]::GetFullPath((Join-Path $startMod '../../starsector-core'))
$startOutput = Join-Path ([IO.Path]::GetTempPath()) ('exile-start-check-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $startOutput | Out-Null
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin/javac.exe' --release 17 `
    -cp "$startCore/*;$(Join-Path $startMod 'jars/ExilesRuleCommands.jar')" `
    -d $startOutput (Join-Path $PSScriptRoot 'ExileStartSetupCheck.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $startMod '../../jre/bin/java.exe') -cp `
    "$startOutput;$startCore/*;$(Join-Path $startMod 'jars/ExilesRuleCommands.jar')" `
    ExileStartSetupCheck
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$startRules = Import-Csv (Join-Path $startMod 'data/campaign/rules.csv')
$startPicked = $startRules | Where-Object { $_.id -eq 'ngcExilePicked' }
if (@($startPicked).Count -ne 1 -or $startPicked.script -match 'ExileAddStartingScript') {
    throw 'Exile selection applies its package before difficulty confirmation'
}
foreach ($startId in @('ngcExileEasyPicked', 'ngcExileNormalPicked', 'ngcExileStartEnd')) {
    $startChoice = $startRules | Where-Object { $_.id -eq $startId }
    if (@($startChoice).Count -ne 1 -or
        $startChoice.script -notmatch 'ExileStartingFines' -or
        [regex]::Matches($startChoice.script, 'ExileAddStartingScript').Count -ne 1 -or
        $startChoice.script -match 'NGCAddCargo|NGCAddCredits|NGCAddStandardStartingScript') {
        throw "$startId must apply one Exile package and mark post-time-pass setup"
    }
}
Write-Output 'PASS: Exile rules apply package once on every completion path'
