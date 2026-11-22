# Janino offline compile-check (see docs/EXILES_BUILD_NOTES.md and CLAUDE.md §7).
# Loads each given class through the game's own Janino JavaSourceClassLoader —
# the same compiler Starsector uses at game start — so loose-script syntax
# errors surface without launching the game.
#
# Usage: & "D:/Fractal Softworks/Starsector_098.5a/mods/Exiles/tools/janino-check.ps1" data.scripts.exiles.ExilesModPlugin [more.classes ...]
# Paths are derived from this script's own location (2026-11-12), so the check always runs against
# the install this copy of the mod sits in: <install>/mods/Exiles/tools/janino-check.ps1.
# Exit code 0 = all classes compiled; 1 = at least one failure.
param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]]$Classes
)

$mod     = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$install = (Resolve-Path (Join-Path $mod '..\..')).Path
$jre     = Join-Path $install 'jre\bin\java.exe'
$core    = Join-Path $install 'starsector-core'
$cp   = "$core\janino.jar;$core\commons-compiler.jar;$core\starfarer.api.jar;$core\lwjgl.jar;$core\lwjgl_util.jar;$core\log4j-1.2.9.jar;$core\json.jar;$core\xstream-1.4.10.jar;$mod\jars\ExilesRuleCommands.jar"
$nexJar = Join-Path $mod '../Nexerelin/jars/ExerelinCore.jar'
if (Test-Path $nexJar) { $cp += ";$nexJar" }

$snippet = 'File[] sp=new File[]{new File(dir)}; JavaSourceClassLoader cl=new JavaSourceClassLoader(ClassLoader.getSystemClassLoader(),sp,null); cl.loadClass(cls); System.out.println(99);'

$fail = 0
foreach ($cls in $Classes) {
    & $jre -cp $cp org.codehaus.commons.compiler.samples.ScriptDemo `
        -te java.lang.Exception `
        -di "org.codehaus.janino.JavaSourceClassLoader,java.io.File" `
        -pn dir,cls -pt java.lang.String,java.lang.String `
        $snippet $mod $cls
    Write-Output "$cls EXIT=$LASTEXITCODE"
    if ($LASTEXITCODE -ne 0) { $fail = 1 }
}
exit $fail
