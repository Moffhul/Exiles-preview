# Offline regression harness for ExileBlackFleetLogistics (see docs/EXILES_BLACK_FLEET_SYSTEMS.md,
# freight section). Runs tools/ExileBlackFleetLogisticsCheck.java through the game's own Janino.
#
# Version-independent (2026-09-19): the class and method names travel as ScriptDemo parameters,
# never as quoted literals inside the snippet -- Windows PowerShell 5.1 strips inner double quotes
# from native-command arguments, which made the previous form fail with "Unknown variable or type".
$bfMod = Split-Path $PSScriptRoot -Parent
$bfCore = [IO.Path]::GetFullPath((Join-Path $bfMod '../../starsector-core'))
$bfClasspath = "$bfCore/janino.jar;$bfCore/commons-compiler.jar;$bfCore/starfarer.api.jar;$bfCore/lwjgl.jar;$bfCore/lwjgl_util.jar;$bfCore/log4j-1.2.9.jar;$bfCore/json.jar;$bfCore/xstream-1.4.10.jar"
$bfSnippet = 'JavaSourceClassLoader cl=new JavaSourceClassLoader(ClassLoader.getSystemClassLoader(),new File[]{new File(dir)},null); cl.loadClass(cls).getMethod(method,new Class[]{String.class}).invoke(null,new Object[]{dir});'
& (Join-Path $bfMod '../../jre/bin/java.exe') --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.desktop/java.awt.font=ALL-UNNAMED -cp $bfClasspath org.codehaus.commons.compiler.samples.ScriptDemo -te java.lang.Exception -di 'org.codehaus.janino.JavaSourceClassLoader,java.io.File' -pn dir,cls,method -pt java.lang.String,java.lang.String,java.lang.String $bfSnippet $bfMod tools.ExileBlackFleetLogisticsCheck run
exit $LASTEXITCODE
