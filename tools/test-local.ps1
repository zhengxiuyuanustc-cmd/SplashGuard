param([string]$JdkRoot=$env:JAVA_HOME,[string]$OutDir)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$OutDir){$OutDir=Join-Path $project 'build/pure-tests'}
$OutDir=[IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$java='java';$javac='javac'
if($JdkRoot){
    $suffix=if($env:OS -eq 'Windows_NT'){'.exe'}else{''}
    $java=Join-Path $JdkRoot "bin/java$suffix"
    $javac=Join-Path $JdkRoot "bin/javac$suffix"
}
# All files here are Android-independent. Keep local and CI checks on the same list.
$policies=@('AdRules','SkipSession','ForegroundWatch','OnboardingPolicy','CandidateSelection','RuleSpec','RuleEvaluator','ActionPolicy','OfflineRuleStore','DiagnosticSample','RuleJsonCodec','ConfigBackup','DeviceGuidePolicy','BrandGuides','RuleCatalog','RuleRuntime','RuleReplay','ReplayCaptureSession','CompatibilityReport','SignedRulePack','RuleUpdateState','RuleStateStorage','RuleTrust')
$checks=@('AdRulesTest','SkipSessionTest','ForegroundWatchTest','OnboardingPolicyTest','CandidateSelectionTest','RuleEvaluatorTest','ActionPolicyTest','OfflineRuleStoreTest','DiagnosticSampleTest','RuleJsonCodecTest','ConfigBackupTest','DeviceGuidePolicyTest','RuleCatalogTest','BiliWarmReturnTest','RuleRuntimeTest','RuleReplayTest','ReplayCaptureSessionTest','CompatibilityReportTest','RuleReplayCorpusTest','SignedRulePackTest','RuleUpdateStateTest','RuleStateStorageTest','RuleTrustTest')
$sources=@($policies | ForEach-Object {Join-Path $project "app/src/main/java/org/splashguard/app/$_.java"})+@($checks | ForEach-Object {Join-Path $project "tests/$_.java"})
foreach($source in $sources){if(!(Test-Path -LiteralPath $source)){throw "Missing safety check source: $source"}}
& $javac -encoding UTF-8 -d $OutDir @sources
if($LASTEXITCODE -ne 0){throw 'Pure Java compilation failed'}
foreach($check in $checks){& $java ("-Dsplashguard.fixtureRoot="+(Join-Path $project "tests/fixtures/rule-replay")) ("-Dsplashguard.authorityRoot="+(Join-Path $project "examples/offline-rules")) -cp $OutDir $check;if($LASTEXITCODE -ne 0){throw "Safety checks failed: $check"}}
Write-Output "Completed $($checks.Count) pure Java test programs."
