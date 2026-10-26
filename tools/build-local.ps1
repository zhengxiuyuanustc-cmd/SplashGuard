param([Parameter(Mandatory=$true)][string]$SdkRoot,[Parameter(Mandatory=$true)][string]$JdkRoot,[string]$OutDir)
$ErrorActionPreference='Stop'
$project = Split-Path $PSScriptRoot -Parent
$appConfig = Get-Content (Join-Path $project 'app\build.gradle') -Raw
if ($appConfig -notmatch "versionName\s+'([^']+)'") { throw 'Missing versionName in app/build.gradle' }
$versionName=$Matches[1]
if ($appConfig -notmatch 'versionCode\s+(\d+)') { throw 'Missing versionCode in app/build.gradle' }
$versionCode=$Matches[1]
if (!$OutDir) { $OutDir = Join-Path $project 'build' }
$OutDir = [IO.Path]::GetFullPath($OutDir)
$platform = Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$tools = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$android = Join-Path $platform.FullName 'android.jar'
$java = Join-Path $JdkRoot 'bin\java.exe'
$javac = Join-Path $JdkRoot 'bin\javac.exe'
$jar = Join-Path $JdkRoot 'bin\jar.exe'
$keytool = Join-Path $JdkRoot 'bin\keytool.exe'
function Run([string]$Program,[string[]]$Arguments) { & $Program @Arguments; if($LASTEXITCODE -ne 0) { throw "Build tool failed: $Program" } }
foreach($name in @('classes','generated','dex','tests')) { New-Item -ItemType Directory -Path (Join-Path $OutDir $name) -Force | Out-Null }
$env:TEMP=$OutDir
$env:TMP=$OutDir
$res = Join-Path $project 'app\src\main\res'
Run (Join-Path $tools.FullName 'aapt2.exe') @('compile','--dir',$res,'-o',(Join-Path $OutDir 'resources.zip'))
$manifest = (Get-Content (Join-Path $project 'app\src\main\AndroidManifest.xml') -Raw).Replace('<manifest xmlns:', '<manifest package="org.splashguard.app" xmlns:').Replace('<application ', '<application android:debuggable="true" ')
$manifestPath = Join-Path $OutDir 'AndroidManifest.xml'
Set-Content -LiteralPath $manifestPath -Value $manifest -Encoding utf8
Run (Join-Path $tools.FullName 'aapt2.exe') @('link','-I',$android,'--manifest',$manifestPath,'--java',(Join-Path $OutDir 'generated'),'--min-sdk-version','26','--target-sdk-version','35','--version-code',$versionCode,'--version-name',$versionName,'-o',(Join-Path $OutDir 'unsigned.apk'),(Join-Path $OutDir 'resources.zip'))
$sources = @(Get-ChildItem (Join-Path $project 'app\src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName) + @(Get-ChildItem (Join-Path $OutDir 'generated') -Filter '*.java' -Recurse | ForEach-Object FullName)
Run $javac (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',($android + ';' + (Join-Path $tools.FullName 'core-lambda-stubs.jar')),'-d',(Join-Path $OutDir 'classes')) + $sources)
Run $jar @("-J-Djava.io.tmpdir=$OutDir",'cf',(Join-Path $OutDir 'classes.jar'),'-C',(Join-Path $OutDir 'classes'),'.')
Run $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output',(Join-Path $OutDir 'dex'),(Join-Path $OutDir 'classes.jar'))
Run $jar @("-J-Djava.io.tmpdir=$OutDir",'uf',(Join-Path $OutDir 'unsigned.apk'),'-C',(Join-Path $OutDir 'dex'),'classes.dex')
Run (Join-Path $tools.FullName 'zipalign.exe') @('-f','-p','4',(Join-Path $OutDir 'unsigned.apk'),(Join-Path $OutDir 'aligned.apk'))
$key = Join-Path $OutDir 'local-debug.keystore'
if (!(Test-Path -LiteralPath $key)) { Run $keytool @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=SplashGuard Local Development','-keyalg','RSA','-keysize','2048','-validity','10000') }
Run $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',$key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',(Join-Path $OutDir "SplashGuard-$versionName-preview.apk"),(Join-Path $OutDir 'aligned.apk'))
Run $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'verify','--verbose',(Join-Path $OutDir "SplashGuard-$versionName-preview.apk"))
& (Join-Path $PSScriptRoot 'test-local.ps1') -JdkRoot $JdkRoot -OutDir (Join-Path $OutDir 'tests')
Write-Output ('APK: ' + (Join-Path $OutDir "SplashGuard-$versionName-preview.apk"))
