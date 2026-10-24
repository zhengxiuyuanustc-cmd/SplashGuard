param([Parameter(Mandatory=$true)][string]$SdkRoot,[Parameter(Mandatory=$true)][string]$JdkRoot,[Parameter(Mandatory=$true)][string]$OutDir,[Parameter(Mandatory=$true)][string]$DebugKeystore)
$ErrorActionPreference='Stop'
$platform=Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$tools=Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$android=Join-Path $platform.FullName 'android.jar'
$java=Join-Path $JdkRoot 'bin\java.exe'
$jar=Join-Path $JdkRoot 'bin\jar.exe'
function Run([string]$Program,[string[]]$Arguments){& $Program @Arguments;if($LASTEXITCODE -ne 0){throw "Tool failed: $Program"}}
foreach($dir in @('classes','dex')){New-Item -ItemType Directory (Join-Path $OutDir $dir) -Force|Out-Null}
$env:TEMP=$OutDir;$env:TMP=$OutDir
Run (Join-Path $tools.FullName 'aapt2.exe') @('link','-I',$android,'--manifest',(Join-Path $PSScriptRoot 'fixture\AndroidManifest.xml'),'--min-sdk-version','26','--target-sdk-version','35','-o',(Join-Path $OutDir 'unsigned.apk'))
Run (Join-Path $JdkRoot 'bin\javac.exe') @('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',($android+';'+(Join-Path $tools.FullName 'core-lambda-stubs.jar')),'-d',(Join-Path $OutDir 'classes'),(Join-Path $PSScriptRoot 'fixture\FixtureActivity.java'))
Run $jar @("-J-Djava.io.tmpdir=$OutDir",'cf',(Join-Path $OutDir 'classes.jar'),'-C',(Join-Path $OutDir 'classes'),'.')
Run $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output',(Join-Path $OutDir 'dex'),(Join-Path $OutDir 'classes.jar'))
Run $jar @("-J-Djava.io.tmpdir=$OutDir",'uf',(Join-Path $OutDir 'unsigned.apk'),'-C',(Join-Path $OutDir 'dex'),'classes.dex')
Run (Join-Path $tools.FullName 'zipalign.exe') @('-f','-p','4',(Join-Path $OutDir 'unsigned.apk'),(Join-Path $OutDir 'aligned.apk'))
Run $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',$DebugKeystore,'--ks-pass','pass:android','--key-pass','pass:android','--out',(Join-Path $OutDir 'fixture.apk'),(Join-Path $OutDir 'aligned.apk'))
