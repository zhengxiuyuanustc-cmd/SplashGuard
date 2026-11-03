#Requires -Version 7.6
param(
    [Parameter(Mandatory=$true)][string]$PayloadFile,
    [Parameter(Mandatory=$true)][string]$KeyFile,
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference='Stop'
if(-not $IsWindows){throw 'This author tool uses a dedicated Windows DPAPI CurrentUser key.'}
$authorityPath=Join-Path $PSScriptRoot 'rule-authority.json'
$authority=Get-Content -LiteralPath $authorityPath -Raw -Encoding UTF8|ConvertFrom-Json
if($authority.schemaVersion -ne 1 -or $authority.kid -ne 'splashguard.rules.20261027' -or $authority.publisher -ne 'splashguard' -or $authority.channel -ne 'community'){throw 'Unexpected reviewed authority configuration.'}
if(Test-Path -LiteralPath $OutputFile){throw 'Output file already exists; select a new path.'}
$payload=[IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $PayloadFile))
if($payload.Length -lt 1 -or $payload.Length -gt 48000){throw 'Rule payload must be between 1 and 48000 bytes.'}
$utf8=[Text.UTF8Encoding]::new($false,$true)
$json=$utf8.GetString($payload)
$pack=$json|ConvertFrom-Json
if($pack.schemaVersion -ne 1 -or $pack.id -notin @($authority.allowedPackIds) -or $pack.version -lt 1 -or $pack.version -gt 1000000){throw 'Payload is outside this rule-authority scope.'}
# Final catalog/tightening validation is also mandatory in the Android importer.
$rsa=[Security.Cryptography.RSA]::Create()
$privateBytes=$null
function Url64([byte[]]$bytes){return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')}
try {
    $entropy=[Text.Encoding]::UTF8.GetBytes('SplashGuard rule-authority v1')
    $privateBytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeyFile)),$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $consumed=0;$rsa.ImportPkcs8PrivateKey($privateBytes,[ref]$consumed)
    if($consumed -ne $privateBytes.Length -or $rsa.KeySize -ne 3072){throw 'Unexpected dedicated rule key.'}
    $publicBytes=$rsa.ExportSubjectPublicKeyInfo()
    $fingerprint=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($publicBytes)).ToLowerInvariant()
    if($fingerprint -ne $authority.publicKeySha256 -or [Convert]::ToBase64String($publicBytes) -ne $authority.publicKeySpkiBase64){throw 'Key does not match the reviewed rule authority.'}
    $header=$utf8.GetBytes((@{alg='RS256';kid=$authority.kid;typ='splashguard-rule-limits+jws'}|ConvertTo-Json -Compress))
    $inputText=(Url64 $header)+'.'+(Url64 $payload)
    $signature=$rsa.SignData([Text.Encoding]::ASCII.GetBytes($inputText),[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $envelope=$inputText+'.'+(Url64 $signature)
    if($envelope.Length -gt 65536){throw 'Signed envelope exceeds the importer limit.'}
    $outputStream=[IO.File]::Open([IO.Path]::GetFullPath($OutputFile),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$outputBytes=[Text.Encoding]::ASCII.GetBytes($envelope);$outputStream.Write($outputBytes,0,$outputBytes.Length)}finally{$outputStream.Dispose()}
    Write-Output 'Signed the exact input bytes with the dedicated local rule key. Validate with the App importer before distributing.'
} finally {
    if($null -ne $privateBytes){[Array]::Clear($privateBytes,0,$privateBytes.Length)}
    $rsa.Dispose()
}
