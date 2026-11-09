# Copy to <_ENV>\env.ps1 and edit the two roots for this machine.
# Dot-source it before any Gradle or Flutter command:  . <_ENV>\env.ps1

$Toolchains = 'D:\_toolchains'
$EnvRoot    = 'D:\_env'

$AndroidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$env:JAVA_HOME        = Join-Path $Toolchains 'jdk-17'
$env:GRADLE_USER_HOME = if (Test-Path (Join-Path $env:USERPROFILE '.gradle')) { Join-Path $env:USERPROFILE '.gradle' } else { Join-Path $EnvRoot 'gradle-home' }
$env:PUB_CACHE        = Join-Path $EnvRoot 'pub-cache'
$env:FLUTTER_ROOT     = Join-Path $Toolchains 'flutter'
$env:ANDROID_HOME     = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk

# flutter test talks to flutter_tester over loopback; a machine proxy breaks it.
$env:NO_PROXY = '127.0.0.1,localhost,::1'
$env:no_proxy = $env:NO_PROXY

$env:PATH = @(
    (Join-Path $env:JAVA_HOME 'bin')
    (Join-Path $env:FLUTTER_ROOT 'bin')
    (Join-Path $AndroidSdk 'platform-tools')
    $env:PATH
) -join ';'
