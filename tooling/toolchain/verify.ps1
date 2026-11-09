#requires -Version 5.1
<#
.SYNOPSIS
    Verify the pinned toolchain against versions.json and gradle/libs.versions.toml.
#>
[CmdletBinding()]
param(
    [string]$Toolchains,
    [string]$EnvRoot
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = (Resolve-Path (Join-Path $here '..\..')).Path
$parent = Split-Path $repo -Parent
if (-not $Toolchains) { $Toolchains = Join-Path $parent '_toolchains' }
if (-not $EnvRoot)    { $EnvRoot    = Join-Path $parent '_env' }
$Toolchains = [IO.Path]::GetFullPath($Toolchains)
$EnvRoot    = [IO.Path]::GetFullPath($EnvRoot)

$versions = Get-Content -LiteralPath (Join-Path $here 'versions.json') -Raw | ConvertFrom-Json
$failures = [System.Collections.Generic.List[string]]::new()
$rows = [System.Collections.Generic.List[object]]::new()

function Add-Row([string]$name, [string]$expected, [string]$actual, [bool]$ok) {
    $rows.Add([pscustomobject]@{ Component = $name; Expected = $expected; Actual = $actual; OK = $ok })
    if (-not $ok) { $failures.Add("$name`: expected $expected, got $actual") }
}

function Get-FirstLine([scriptblock]$block) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        (& $block 2>&1 | Select-Object -First 1).ToString().Trim()
    } catch {
        $_.Exception.Message
    } finally {
        $ErrorActionPreference = $previous
    }
}

$jdk = Join-Path $Toolchains 'jdk-17'
$javaActual = Get-FirstLine { & (Join-Path $jdk 'bin\java.exe') -version }
Add-Row 'JDK' $versions.java.version $javaActual ($javaActual -match [regex]::Escape($versions.java.version))

$gradleHome = Join-Path $Toolchains "gradle-$($versions.gradle.version)\gradle-$($versions.gradle.version)"
$gradleActual = Get-FirstLine { & (Join-Path $gradleHome 'bin\gradle.bat') --version | Where-Object { $_ -match '^Gradle ' } }
Add-Row 'Gradle' $versions.gradle.version $gradleActual ($gradleActual -match [regex]::Escape($versions.gradle.version))

$flutter = Join-Path $Toolchains 'flutter'
$flutterActual = Get-FirstLine { & (Join-Path $flutter 'bin\flutter.bat') --version }
Add-Row 'Flutter' $versions.flutter.version $flutterActual ($flutterActual -match [regex]::Escape($versions.flutter.version))

$dartActual = Get-FirstLine { & (Join-Path $flutter 'bin\dart.bat') --version }
Add-Row 'Dart' $versions.flutter.dart $dartActual ($dartActual -match [regex]::Escape($versions.flutter.dart))

$android = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
foreach ($item in @(
        @{ Name = "platforms\android-$($versions.android.compileSdk)"; Expected = "android-$($versions.android.compileSdk)" },
        @{ Name = "build-tools\$($versions.android.buildTools)"; Expected = $versions.android.buildTools },
        @{ Name = "ndk\$($versions.android.ndk)"; Expected = $versions.android.ndk }
    )) {
    $path = Join-Path $android $item.Name
    Add-Row "Android $($item.Name)" $item.Expected $(if (Test-Path -LiteralPath $path) { 'present' } else { 'missing' }) (Test-Path -LiteralPath $path)
}

$expectedGradleHome = if (Test-Path (Join-Path $env:USERPROFILE '.gradle')) { Join-Path $env:USERPROFILE '.gradle' } else { Join-Path $EnvRoot 'gradle-home' }

$catalogPath = Join-Path $repo 'gradle\libs.versions.toml'
$catalog = Get-Content -LiteralPath $catalogPath -Raw
$agp = [regex]::Match($catalog, '(?m)^agp\s*=\s*"([^"]+)"').Groups[1].Value
$kotlin = [regex]::Match($catalog, '(?m)^kotlin\s*=\s*"([^"]+)"').Groups[1].Value
Add-Row 'AGP (catalog)' $versions.android.agp $agp ($agp -eq $versions.android.agp)
Add-Row 'Kotlin (catalog)' $versions.android.kotlin $kotlin ($kotlin -eq $versions.android.kotlin)

foreach ($entry in @(
        @{ Name = 'JAVA_HOME'; Actual = $env:JAVA_HOME; Expected = $jdk },
        @{ Name = 'GRADLE_USER_HOME'; Actual = $env:GRADLE_USER_HOME; Expected = $expectedGradleHome },
        @{ Name = 'FLUTTER_ROOT'; Actual = $env:FLUTTER_ROOT; Expected = $flutter },
        @{ Name = 'PUB_CACHE'; Actual = $env:PUB_CACHE; Expected = (Join-Path $EnvRoot 'pub-cache') }
    )) {
    $actual = if ($entry.Actual) { [IO.Path]::GetFullPath($entry.Actual) } else { '<unset>' }
    Add-Row $entry.Name $entry.Expected $actual ($actual -eq [IO.Path]::GetFullPath($entry.Expected))
}

$rows | Format-Table -AutoSize
if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Host "FAILED ($($failures.Count))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host ''
    Write-Host "Load the environment first:  . `"$(Join-Path $EnvRoot 'env.ps1')`""
    exit 1
}
Write-Host ''
Write-Host 'OK: toolchain matches versions.json and the version catalog.' -ForegroundColor Green

exit 0
