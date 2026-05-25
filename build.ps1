$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$platform = Join-Path $sdk "platforms\android-31\android.jar"
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$javaHome = "C:\Program Files\Android\Android Studio1\jbr"
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome "bin") + ";" + $env:PATH
$javac = Join-Path $javaHome "bin\javac.exe"
$jar = Join-Path $javaHome "bin\jar.exe"
$aapt2 = Join-Path $buildTools "aapt2.exe"
$d8 = Join-Path $buildTools "d8.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$keystore = "E:\Work\MG4\apktool\emulator.keystore"

$build = Join-Path $root "build"
$dist = Join-Path $root "dist"
$classes = Join-Path $build "classes"
$stubClasses = Join-Path $build "stubclasses"
$dex = Join-Path $build "dex"
$compiled = Join-Path $build "compiled"

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes, $stubClasses, $dex, $compiled, $dist | Out-Null
$localAndroidJar = Join-Path $build "android.jar"
Copy-Item -LiteralPath $platform -Destination $localAndroidJar -Force

& $aapt2 compile --dir (Join-Path $root "app\src\main\res") -o (Join-Path $compiled "resources.zip")

& $aapt2 link `
  -o (Join-Path $build "unsigned-unaligned.apk") `
  -I $localAndroidJar `
  --manifest (Join-Path $root "app\src\main\AndroidManifest.xml") `
  --min-sdk-version 28 `
  --target-sdk-version 31 `
  --version-code 4 `
  --version-name 0.4 `
  (Join-Path $compiled "resources.zip")

$sources = Get-ChildItem -Path (Join-Path $root "app\src\main\java") -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$stubSources = Get-ChildItem -Path (Join-Path $root "stubs") -Recurse -Filter *.java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
if ($stubSources) {
  & $javac -source 1.8 -target 1.8 -classpath $localAndroidJar -d $stubClasses $stubSources
  if ($LASTEXITCODE -ne 0) { throw "stub javac failed with exit code $LASTEXITCODE" }
}

$compileClasspath = $localAndroidJar + ";" + $stubClasses
& $javac -source 1.8 -target 1.8 -classpath $compileClasspath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Push-Location $classes
try {
  & $jar cf (Join-Path $build "classes.jar") .
}
finally {
  Pop-Location
}

& $d8 --min-api 28 --output $dex (Join-Path $build "classes.jar")
if ($LASTEXITCODE -ne 0) { throw "d8 failed with exit code $LASTEXITCODE" }
Copy-Item -LiteralPath (Join-Path $dex "classes.dex") -Destination (Join-Path $build "classes.dex") -Force
$saicSdkDex = Join-Path $root "..\known_good_seats_base\build\apk\classes5.dex"
if (Test-Path $saicSdkDex) {
  Copy-Item -LiteralPath $saicSdkDex -Destination (Join-Path $build "classes2.dex") -Force
}

Push-Location $build
try {
  & $buildTools\aapt.exe add unsigned-unaligned.apk classes.dex | Out-Null
  if (Test-Path .\classes2.dex) {
    & $buildTools\aapt.exe add unsigned-unaligned.apk classes2.dex | Out-Null
  }
}
finally {
  Pop-Location
}

& $zipalign -f 4 (Join-Path $build "unsigned-unaligned.apk") (Join-Path $build "aligned.apk")

& $apksigner sign `
  --ks $keystore `
  --ks-key-alias platform `
  --ks-pass pass:android `
  --key-pass pass:android `
  --out (Join-Path $dist "MG4-HVACFloat-V0.5.apk") `
  (Join-Path $build "aligned.apk")

& $apksigner verify --verbose (Join-Path $dist "MG4-HVACFloat-V0.5.apk")
Get-Item (Join-Path $dist "MG4-HVACFloat-V0.5.apk")
