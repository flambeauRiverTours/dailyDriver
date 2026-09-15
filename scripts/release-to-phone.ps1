<#
.SYNOPSIS
    Cuts an Android release and installs it on the phone over wireless debugging.

.DESCRIPTION
    Tags a commit, waits for the release workflow to attach a signed APK, connects to the phone,
    and installs over the top. One command for what docs/releasing.md describes as four.

    Everything here is the documented flow, not a shortcut around it:
      - the tag is the version (build.gradle.kts reads it), so no source file is edited
      - the APK comes from the GitHub Release, so it is the CI-signed one
      - install is `adb install -r`, which keeps the database because every release shares a key

.PARAMETER Version
    Release version, e.g. 0.4.0. Tagged as android-v<Version>.

.PARAMETER Device
    The phone's wireless-debugging address for CONNECT, e.g. 192.168.2.49:40253.
    Read it from Settings -> Developer options -> Wireless debugging (top of the screen).

.PARAMETER PairAddress
    Address for PAIRING, e.g. 192.168.2.49:41234. Only needed once ever, from
    "Pair device with pairing code". The pairing port is NOT the connect port.

.PARAMETER PairCode
    The six-digit code shown beside the pairing address.

.PARAMETER Tag
    Install an existing release instead of cutting a new one, e.g. android-v0.3.0.

.EXAMPLE
    .\scripts\release-to-phone.ps1
    Prompts for the version and the device address.

.EXAMPLE
    .\scripts\release-to-phone.ps1 -Version 0.4.0 -Device 192.168.2.49:40253

.EXAMPLE
    .\scripts\release-to-phone.ps1 -Tag android-v0.3.0 -Device 192.168.2.49:40253
    Reinstalls an existing release; cuts nothing.
#>

[CmdletBinding()]
param(
    [string]$Version,
    [string]$Device,
    [string]$PairAddress,
    [string]$PairCode,
    [string]$Tag
)

$ErrorActionPreference = 'Stop'

function Write-Step { param($Message) Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Note { param($Message) Write-Host "    $Message" -ForegroundColor DarkGray }
function Fail      { param($Message) Write-Host "`nX  $Message" -ForegroundColor Red; exit 1 }

# --- locate the repo and adb ---------------------------------------------------------------------

$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $RepoRoot 'android/build.gradle.kts'))) {
    Fail "Expected to find android/ next to scripts/. Run this from inside the repo."
}
Set-Location $RepoRoot

# adb is deliberately not on PATH on this machine -- Gradle resolves it from sdk.dir, so we do the
# same rather than asking anyone to edit their environment. The property file escapes its colons
# and backslashes (`C\:\\Users\\...`), which is why this unescapes before using the value.
function Resolve-Adb {
    $props = Join-Path $RepoRoot 'android/local.properties'
    if (Test-Path $props) {
        $line = Select-String -Path $props -Pattern '^\s*sdk\.dir\s*=\s*(.+)$' | Select-Object -First 1
        if ($line) {
            $sdk = $line.Matches[0].Groups[1].Value.Trim() -replace '\\:', ':' -replace '\\\\', '\'
            $candidate = Join-Path $sdk 'platform-tools/adb.exe'
            if (Test-Path $candidate) { return $candidate }
        }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $default = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
    if (Test-Path $default) { return $default }

    Fail "Could not find adb. Set sdk.dir in android/local.properties, or put adb on PATH."
}

$Adb = Resolve-Adb
Write-Note "adb: $Adb"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Fail "The GitHub CLI (gh) is required to publish and download the release. See cli.github.com."
}

# --- work out what we are installing --------------------------------------------------------------

$CutRelease = -not $Tag

if ($CutRelease) {
    if (-not $Version) { $Version = Read-Host 'Release version (e.g. 0.4.0)' }
    $Version = $Version.Trim().TrimStart('v')

    if ($Version -notmatch '^\d+\.\d+\.\d+$') {
        Fail "Version must be three dotted numbers, e.g. 0.4.0 -- got '$Version'."
    }
    # versionCode packs two digits per component (0.2.0 -> 200), so a component reaching three
    # digits would collide with the next one up and Android would read it as a downgrade.
    if (($Version -split '\.' | Where-Object { [int]$_ -gt 99 })) {
        Fail "Each component must be 0-99: versionCode allows two digits each. See docs/releasing.md."
    }
    $Tag = "android-v$Version"
}

if (-not $Device) {
    Write-Note 'Phone: Settings -> Developer options -> Wireless debugging (address at the top).'
    $Device = Read-Host 'Device address for connect (ip:port)'
}
$Device = $Device.Trim()
if ($Device -notmatch '^[\d\.]+:\d+$') { Fail "Device address must look like 192.168.2.49:40253 -- got '$Device'." }

# --- cut the release ------------------------------------------------------------------------------

if ($CutRelease) {
    Write-Step "Cutting $Tag"

    if ((git status --porcelain)) {
        Fail "Working tree is dirty. A tag should point at a commit that exists, not a desk."
    }
    if ((git tag --list $Tag)) {
        Fail "$Tag already exists locally. Use -Tag $Tag to install it, or pick a new version."
    }

    $branch = (git rev-parse --abbrev-ref HEAD).Trim()
    if ($branch -ne 'main') {
        Write-Host "    You are on '$branch', not main." -ForegroundColor Yellow
        if ((Read-Host '    Tag it anyway? (y/N)') -notmatch '^[Yy]') { Fail 'Stopped.' }
    }

    $sha = (git rev-parse --short HEAD).Trim()
    $subject = (git log -1 --pretty=%s).Trim()
    Write-Note "$sha  $subject"
    Write-Host "`n    This pushes a tag and publishes a public release." -ForegroundColor Yellow
    if ((Read-Host "    Create and push $Tag? (y/N)") -notmatch '^[Yy]') { Fail 'Stopped.' }

    git tag $Tag
    git push origin $Tag
    if ($LASTEXITCODE -ne 0) { git tag -d $Tag | Out-Null; Fail 'Tag push failed; local tag removed.' }

    # Watch the run rather than polling for the asset: if CI fails, this says so in seconds instead
    # of spinning until a timeout with no reason given. Tests run again at tag time, so this is a
    # real gate and not a formality.
    Write-Step 'Waiting for the release workflow'
    $runId = $null
    foreach ($attempt in 1..20) {
        Start-Sleep -Seconds 3
        # Filtered by the tag, not just "most recent": a run triggered by a tag push reports the
        # tag as its branch, and grabbing the latest run unfiltered would happily watch someone
        # else's older release to a green finish and then download the wrong APK.
        $runId = gh run list --workflow=android-release.yml --branch $Tag --limit 1 `
            --json databaseId --jq '.[0].databaseId' 2>$null
        if ($runId) { break }
        Write-Note "waiting for the run to appear ($attempt/20)..."
    }
    if (-not $runId) { Fail "No workflow run appeared for $Tag. Check the Actions tab." }

    gh run watch $runId --exit-status
    if ($LASTEXITCODE -ne 0) {
        Fail "The release workflow failed. Detail: gh run view $runId --log-failed"
    }
}

# --- download the APK -----------------------------------------------------------------------------

Write-Step "Downloading the APK from $Tag"

$DownloadDir = Join-Path ([IO.Path]::GetTempPath()) "dailydriver-$Tag"
if (Test-Path $DownloadDir) { Remove-Item $DownloadDir -Recurse -Force }
New-Item -ItemType Directory -Path $DownloadDir | Out-Null

gh release download $Tag --pattern '*.apk' --dir $DownloadDir --clobber
if ($LASTEXITCODE -ne 0) { Fail "Could not download $Tag. Does the release exist and have an APK?" }

$Apk = Get-ChildItem $DownloadDir -Filter *.apk | Select-Object -First 1
if (-not $Apk) { Fail "No .apk in the $Tag release." }
Write-Note "$($Apk.Name)  ($([math]::Round($Apk.Length / 1MB, 1)) MB)"

# --- connect to the phone -------------------------------------------------------------------------

Write-Step "Connecting to $Device"

# A phantom 'offline' entry fails a run with "No online devices found" even when a working device is
# attached, and nothing about the message points at the stale entry. Clearing first is cheap.
& $Adb kill-server | Out-Null
& $Adb start-server | Out-Null

if ($PairAddress) {
    if (-not $PairCode) { $PairCode = Read-Host 'Pairing code' }
    Write-Note "pairing with $PairAddress (one-off; pairing survives reboots)"
    & $Adb pair $PairAddress $PairCode
}

$connect = & $Adb connect $Device 2>&1 | Out-String
Write-Note $connect.Trim()
if ($connect -match 'cannot connect|failed to connect|unable to connect') {
    Fail @"
Could not reach $Device.

The connect port changes whenever wireless debugging is toggled or the phone reboots -- the
pairing survives, but the port does not. Re-read it from the Wireless debugging screen.
If the phone has never been paired with this machine, pass -PairAddress and -PairCode.
"@
}

& $Adb -s $Device wait-for-device

# --- install ----------------------------------------------------------------------------------------

Write-Step 'Installing'

# -s pins the target: with an emulator also attached, adb picks one for you and it picks wrong.
# -r reinstalls over the top and KEEPS THE DATABASE, which works only because every release is
# signed with the same key. Sync does not exist yet, so an uninstall is real data loss.
$install = & $Adb -s $Device install -r $Apk.FullName 2>&1 | Out-String
Write-Note $install.Trim()

if ($install -notmatch 'Success') {
    if ($install -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Fail @"
Signature mismatch: what is on the phone was signed with a different key.

Usually this means a locally built (debug-signed) APK is installed. Uninstalling to fix it takes
the database with it -- see docs/releasing.md before you do that.
"@
    }
    if ($install -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
        Fail "The phone has a newer versionCode. Android refuses downgrades; tag a higher version."
    }
    Fail 'Install failed -- see the adb output above.'
}

Write-Host "`nOK  $Tag installed on $Device" -ForegroundColor Green
Write-Host @"

    It will NOT appear in the app drawer. MainActivity declares CATEGORY_HOME, not
    CATEGORY_LAUNCHER -- this is a home screen, so it has no drawer icon by design.

    Press HOME to see it, or set it as the default:
      Settings -> Apps -> (menu) -> Default apps -> Home app

"@ -ForegroundColor DarkGray
