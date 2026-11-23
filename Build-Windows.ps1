# Gil Odin Control 3 integrated native Odin3Tools + Dock builder
# Reuses the GilAndroidBuildCache SDK/Gradle installed by previous builds.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$cache = Join-Path $env:LOCALAPPDATA 'GilAndroidBuildCache'
$androidHome = Join-Path $cache 'android-sdk'
$gradleHome = Join-Path $cache 'gradle-8.9'
$log = Join-Path $root 'build-log.txt'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
Start-Transcript -Path $log -Force | Out-Null
try {
    Set-Location $root
    $installedStudioJdk = 'C:\Program Files\Android\Android Studio\jbr'
    if (-not $env:JAVA_HOME -and (Test-Path "$installedStudioJdk\bin\java.exe")) {
        $env:JAVA_HOME = $installedStudioJdk
    }
    if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Java 17+ is required (JDK 17 recommended).'
    }
    $version = (& cmd.exe /c 'java -version 2>&1' | Select-Object -First 1).ToString()
    Write-Host "Java: $version"
    if ($version -notmatch '"(17|18|19|2[0-9])\.') { throw 'Java 17+ not found.' }

    if ($env:ANDROID_HOME -and (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'))) {
        $androidHome = $env:ANDROID_HOME
    } elseif ($env:ANDROID_SDK_ROOT -and (Test-Path (Join-Path $env:ANDROID_SDK_ROOT 'platforms\android-35\android.jar'))) {
        $androidHome = $env:ANDROID_SDK_ROOT
    }
    $sdkmanager = Join-Path $androidHome 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (-not (Test-Path $sdkmanager) -and -not (Test-Path (Join-Path $androidHome 'platforms\android-35\android.jar'))) {
        Write-Host 'Downloading Android SDK tools (only if not cached)...'
        $archive = Join-Path $cache 'commandlinetools-win-11076708_latest.zip'
        if (-not (Test-Path $archive)) {
            Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $archive
        }
        $tmp = Join-Path $cache 'sdk-unpack'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        Expand-Archive -Path $archive -DestinationPath $tmp
        New-Item -ItemType Directory -Force -Path (Join-Path $androidHome 'cmdline-tools') | Out-Null
        Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $androidHome 'cmdline-tools\latest')
        $sdkmanager = Join-Path $androidHome 'cmdline-tools\latest\bin\sdkmanager.bat'
    }
    $env:ANDROID_HOME = $androidHome
    $env:ANDROID_SDK_ROOT = $androidHome
    if (-not (Test-Path (Join-Path $androidHome 'platforms\android-35\android.jar'))) {
        if (-not (Test-Path $sdkmanager)) { throw 'Android SDK manager missing. Install SDK 35.' }
        (('y' + [Environment]::NewLine) * 60) | & $sdkmanager --sdk_root=$androidHome --licenses | Out-Null
        & $sdkmanager --sdk_root=$androidHome 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
        if ($LASTEXITCODE -ne 0) { throw 'Android SDK setup failed.' }
    }

    $gradleCmd = Join-Path $gradleHome 'bin\gradle.bat'
    if (-not (Test-Path $gradleCmd)) {
        # Gradle 8.9 was used by previous Gil builds; retain the exact cache.
        Write-Host 'Downloading Gradle 8.9 (only if not cached)...'
        $archive = Join-Path $cache 'gradle-8.9-bin.zip'
        if (-not (Test-Path $archive)) {
            Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $archive
        }
        Expand-Archive -Path $archive -DestinationPath $cache -Force
    }
    if (-not (Test-Path $gradleCmd)) { throw 'Gradle 8.9 unavailable.' }
    $env:GRADLE_USER_HOME = Join-Path $cache 'gradle-user-home'
    & $gradleCmd --no-daemon --no-configuration-cache :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Gradle compilation failed. See build-log.txt.' }
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw 'Gradle succeeded but no APK was produced.' }
    $destination = Join-Path $root 'GilOdinControl3-0.7.0-studio-debug.apk'
    Copy-Item $apk $destination -Force
    Write-Host "[OK] Built: $destination"
    Write-Host 'APK is unsigned with release key; debug signing depends on the local Gradle user profile.'
} catch {
    Write-Host ("[BUILD FAILED] " + $_.Exception.Message) -ForegroundColor Red
    exit 1
} finally {
    Stop-Transcript | Out-Null
}
