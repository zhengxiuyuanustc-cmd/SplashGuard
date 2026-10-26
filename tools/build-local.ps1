param(
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [Parameter(Mandatory=$true)][string]$JdkRoot,
    [string]$OutDir,
    [ValidateSet('Debug','NonDebugPreview','UnsignedValidation')][string]$BuildMode='Debug',
    [string]$SigningKeyStore,
    [string]$SigningKeyAlias,
    [string]$StorePassEnv,
    [string]$KeyPassEnv,
    [string]$ExpectedCertificateSha256
)
$ErrorActionPreference='Stop'
$project = Split-Path $PSScriptRoot -Parent
$appConfig = Get-Content (Join-Path $project 'app\build.gradle') -Raw
if ($appConfig -notmatch "versionName\s+'([^']+)'") { throw 'Missing versionName in app/build.gradle' }
$versionName=$Matches[1]
if ($appConfig -notmatch 'versionCode\s+(\d+)') { throw 'Missing versionCode in app/build.gradle' }
$versionCode=$Matches[1]
if (!$OutDir) { $OutDir = if ($BuildMode -eq 'NonDebugPreview') { Join-Path $project 'build/nondebug-preview' } elseif ($BuildMode -eq 'UnsignedValidation') { Join-Path $project 'build/unsigned-validation' } else { Join-Path $project 'build' } }
$OutDir = [IO.Path]::GetFullPath($OutDir)
$platform = Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$tools = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$android = Join-Path $platform.FullName 'android.jar'
$java = Join-Path $JdkRoot 'bin\java.exe'
$javac = Join-Path $JdkRoot 'bin\javac.exe'
$jar = Join-Path $JdkRoot 'bin\jar.exe'
$keytool = Join-Path $JdkRoot 'bin\keytool.exe'
function Run([string]$Program,[string[]]$Arguments) { & $Program @Arguments; if($LASTEXITCODE -ne 0) { throw "Build tool failed: $Program" } }
# UnsignedValidation is a key-free compile check. Reject even explicitly supplied
# empty signing arguments; this mode never consults password sources or keystores.
if ($BuildMode -eq 'UnsignedValidation') {
    foreach ($signingOption in @('SigningKeyStore','SigningKeyAlias','StorePassEnv','KeyPassEnv','ExpectedCertificateSha256')) {
        if ($PSBoundParameters.ContainsKey($signingOption)) { throw 'UnsignedValidation does not accept any signing options or credentials.' }
    }
}
# Only Debug may create a disposable local development key. NonDebugPreview must
# deliberately reuse a caller-selected existing key and its expected public identity.
if ($BuildMode -eq 'NonDebugPreview') {
    if ([string]::IsNullOrWhiteSpace($SigningKeyStore) -or !(Test-Path -LiteralPath $SigningKeyStore -PathType Leaf)) { throw 'NonDebugPreview requires an existing SigningKeyStore; no key will be created.' }
    $SigningKeyStore = (Resolve-Path -LiteralPath $SigningKeyStore).ProviderPath
    if ([string]::IsNullOrWhiteSpace($SigningKeyAlias)) { throw 'NonDebugPreview requires SigningKeyAlias.' }
    foreach ($credentialName in @($StorePassEnv,$KeyPassEnv)) {
        if ([string]::IsNullOrWhiteSpace($credentialName) -or $credentialName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'Provide StorePassEnv and KeyPassEnv as environment-variable names, not passwords.' }
        if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($credentialName,'Process'))) { throw 'A named signing-password environment variable is empty or unavailable.' }
    }
    $expectedFingerprint = ($ExpectedCertificateSha256 -replace '[:\s]','').ToLowerInvariant()
    if ($expectedFingerprint -notmatch '^[0-9a-f]{64}$') { throw 'NonDebugPreview requires an explicitly verified ExpectedCertificateSha256.' }
    # keytool receives only an environment-variable name; capture the public
    # certificate in memory without logging the certificate or password values.
    $certificateOutput = & $keytool -exportcert -rfc -keystore $SigningKeyStore -alias $SigningKeyAlias '-storepass:env' $StorePassEnv 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the selected signing certificate. Check the key, alias and credential source.' }
    $certificateMatch = [regex]::Match(($certificateOutput -join "`n"),'(?s)-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END CERTIFICATE-----')
    if (!$certificateMatch.Success) { throw 'Cannot decode the selected signing certificate.' }
    $certificateBytes = [Convert]::FromBase64String(($certificateMatch.Groups[1].Value -replace '\s',''))
    $certificateHash = [Security.Cryptography.SHA256]::Create()
    try { $actualFingerprint = ([BitConverter]::ToString($certificateHash.ComputeHash($certificateBytes)) -replace '-','').ToLowerInvariant() }
    finally { $certificateHash.Dispose() }
    if ($actualFingerprint -ne $expectedFingerprint) { throw 'Signing certificate does not match ExpectedCertificateSha256; build stopped before signing.' }
} elseif ($SigningKeyStore -or $SigningKeyAlias -or $StorePassEnv -or $KeyPassEnv -or $ExpectedCertificateSha256) {
    throw 'Explicit signing options are supported only with BuildMode NonDebugPreview.'
}
$apkName = if ($BuildMode -eq 'NonDebugPreview') { "SplashGuard-$versionName-nondebug-preview.apk" } elseif ($BuildMode -eq 'UnsignedValidation') { "SplashGuard-$versionName-unsigned-validation.apk" } else { "SplashGuard-$versionName-preview.apk" }
$apkPath = Join-Path $OutDir $apkName
foreach($name in @('classes','generated','dex','tests')) { New-Item -ItemType Directory -Path (Join-Path $OutDir $name) -Force | Out-Null }
$env:TEMP=$OutDir
$env:TMP=$OutDir
$res = Join-Path $project 'app\src\main\res'
Run (Join-Path $tools.FullName 'aapt2.exe') @('compile','--dir',$res,'-o',(Join-Path $OutDir 'resources.zip'))
[xml]$manifest = Get-Content (Join-Path $project 'app\src\main\AndroidManifest.xml') -Raw -Encoding utf8
[void]$manifest.DocumentElement.SetAttribute('package','org.splashguard.app')
$application = $manifest.SelectSingleNode('/manifest/application')
if ($null -eq $application) { throw 'Missing application element in AndroidManifest.xml' }
$debuggable = $manifest.CreateAttribute('android','debuggable','http://schemas.android.com/apk/res/android')
$debuggable.Value = if ($BuildMode -eq 'Debug') { 'true' } else { 'false' }
[void]$application.Attributes.SetNamedItem($debuggable)
$manifestPath = Join-Path $OutDir 'AndroidManifest.xml'
$manifest.Save($manifestPath)
Run (Join-Path $tools.FullName 'aapt2.exe') @('link','-I',$android,'--manifest',$manifestPath,'--java',(Join-Path $OutDir 'generated'),'--min-sdk-version','26','--target-sdk-version','35','--version-code',$versionCode,'--version-name',$versionName,'-o',(Join-Path $OutDir 'unsigned.apk'),(Join-Path $OutDir 'resources.zip'))
$sources = @(Get-ChildItem (Join-Path $project 'app\src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName) + @(Get-ChildItem (Join-Path $OutDir 'generated') -Filter '*.java' -Recurse | ForEach-Object FullName)
Run $javac (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',($android + ';' + (Join-Path $tools.FullName 'core-lambda-stubs.jar')),'-d',(Join-Path $OutDir 'classes')) + $sources)
Run $jar @("-J-Djava.io.tmpdir=$OutDir",'cf',(Join-Path $OutDir 'classes.jar'),'-C',(Join-Path $OutDir 'classes'),'.')
Run $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output',(Join-Path $OutDir 'dex'),(Join-Path $OutDir 'classes.jar'))
Run $jar @("-J-Djava.io.tmpdir=$OutDir",'uf',(Join-Path $OutDir 'unsigned.apk'),'-C',(Join-Path $OutDir 'dex'),'classes.dex')
Run (Join-Path $tools.FullName 'zipalign.exe') @('-f','-p','4',(Join-Path $OutDir 'unsigned.apk'),(Join-Path $OutDir 'aligned.apk'))
if ($BuildMode -eq 'UnsignedValidation') {
    Copy-Item -LiteralPath (Join-Path $OutDir 'aligned.apk') -Destination $apkPath -Force
    Write-Output 'UNSIGNED VALIDATION ONLY: not installable and not for publication.'
} elseif ($BuildMode -eq 'NonDebugPreview') {
    Run $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',$SigningKeyStore,'--ks-key-alias',$SigningKeyAlias,'--ks-pass',("env:"+$StorePassEnv),'--key-pass',("env:"+$KeyPassEnv),'--out',$apkPath,(Join-Path $OutDir 'aligned.apk'))
} else {
    $key = Join-Path $OutDir 'local-debug.keystore'
    if (!(Test-Path -LiteralPath $key)) { Run $keytool @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=SplashGuard Local Development','-keyalg','RSA','-keysize','2048','-validity','10000') }
    Run $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',$key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',$apkPath,(Join-Path $OutDir 'aligned.apk'))
}
if ($BuildMode -eq 'NonDebugPreview') {
    $verificationOutput = & $java -jar (Join-Path $tools.FullName 'lib\apksigner.jar') verify --verbose --print-certs $apkPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'NonDebugPreview APK signature verification failed.' }
    $signerFingerprints = [regex]::Matches(($verificationOutput -join "`n"),'(?m)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$')
    if ($signerFingerprints.Count -ne 1 -or $signerFingerprints[0].Groups[1].Value.ToLowerInvariant() -ne $expectedFingerprint) { throw 'Final APK signer does not match ExpectedCertificateSha256.' }
    Write-Output ('Verified signing certificate SHA-256: ' + $expectedFingerprint)
} elseif ($BuildMode -eq 'Debug') {
    Run $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'verify','--verbose',$apkPath)
}
& (Join-Path $PSScriptRoot 'test-local.ps1') -JdkRoot $JdkRoot -OutDir (Join-Path $OutDir 'tests')
Write-Output ('Build mode: ' + $BuildMode)
Write-Output ('APK: ' + $apkPath)
