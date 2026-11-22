# Rebuild the precompiled commands that rules.csv needs during early startup.
#
# Version-independent (2026-09-19). The previous ZipFile.CreateFromDirectory call worked only
# under PowerShell 7: Windows PowerShell 5.1 does not preload the compression assemblies, and its
# .NET Framework writes zip entries with BACKSLASH paths, which Java's zip reader cannot resolve
# (the jar loaded but every class was "not found"). Entries are now written one at a time with
# explicit forward-slash names, which both runtimes accept and Java requires.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$buildMod = Split-Path $PSScriptRoot -Parent
$buildCore = [IO.Path]::GetFullPath((Join-Path $buildMod '../../starsector-core'))
$buildJava = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin'
$buildOutput = Join-Path ([IO.Path]::GetTempPath()) ('exile-rule-commands-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildOutput | Out-Null
$buildSources = @(Get-ChildItem (Join-Path $buildMod 'src/data/scripts/rulecmd') -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $buildJava 'javac.exe') --release 17 -cp "$buildCore/*" -d $buildOutput $buildSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$buildArchive = Join-Path ([IO.Path]::GetTempPath()) ('exile-rule-commands-' + [Guid]::NewGuid().ToString('N') + '.jar')
$buildRoot = (Get-Item $buildOutput).FullName.TrimEnd('\', '/')
$buildZip = [IO.Compression.ZipFile]::Open($buildArchive, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($buildFile in (Get-ChildItem $buildOutput -Recurse -File | Sort-Object FullName)) {
        $buildEntry = $buildFile.FullName.Substring($buildRoot.Length).TrimStart('\', '/').Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($buildZip, $buildFile.FullName, $buildEntry, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $buildZip.Dispose()
}
Move-Item -LiteralPath $buildArchive -Destination (Join-Path $buildMod 'jars/ExilesRuleCommands.jar') -Force
Write-Output 'ExilesRuleCommands.jar rebuilt'
