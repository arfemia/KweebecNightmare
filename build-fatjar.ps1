# Build ONE self-contained KweebecNightmare jar that EMBEDS its two hard
# dependencies (Perfect Utils + ZiggfreedCommon) as engine SUB-PLUGINS.
#
# Why this is safe (and why the usual "never bundle a dependency jar" rule does
# NOT apply here): Hytale's PluginManager supports multiple plugins in ONE jar via
# the manifest.json "SubPlugins" array. Every sub-plugin shares the SAME single
# PluginClassLoader as the parent (PendingLoadJavaPlugin.createSubPendingLoadPlugin
# reuses this.urlClassLoader), so there is ONE copy of every class - no two-
# classloader identity break. The "never bundle" warning is about shipping the dep
# as a SECOND standalone jar at the same time, which double-loads. A single multi-
# plugin jar is the engine-sanctioned way to ship them together.
#
# Layout produced (verified against the 0.5.3 decompile):
#   - Top-level manifest = KweebecNightmare (the PRIMARY in-game identity; keeps the asset pack)
#   - SubPlugins[0]       = ZiggfreedCommon (IncludesAssetPack -> false)
#   - SubPlugins[1]       = Perfect Utils   (IncludesAssetPack -> false)
#   The parent loads FIRST and manifest.inherit() injects a parent-dependency into each
#   sub, so the resolved order is Kweebec -> Common -> Perfect Utils. That REVERSE of the
#   standalone order is safe: every ziggfreed-common framework-asset read happens lazily
#   on LoadedAssetsEvent (after all setups), and Perfect Utils' AggroAPI/StunMobAPI are
#   resolved live at gameplay, not cached at Kweebec setup. Kweebec's hard-dep
#   declarations are dropped from this manifest (the subs are in the same jar; keeping
#   them would form an inherit() cycle that aborts load-order resolution).
#   Exactly ONE asset pack (Kweebec's, registered first) carries the merged Server/ +
#   Common/ tree, so the engine never scans the merged jar root more than once.
#
#   .\build-fatjar.ps1                 # merge existing build/libs jars into one fat jar
#   .\build-fatjar.ps1 -Build          # rebuild all three source jars first, then merge
#   .\build-fatjar.ps1 -Install        # also install to $env:HYTALE_MODS_DIR (cleans the
#                                       #   old standalone Common / Perfect Utils jars first)
#   .\build-fatjar.ps1 -ModsDir <path> # explicit install target
param(
    [switch]$Build,
    [switch]$Install,
    [string]$ModsDir = $env:HYTALE_MODS_DIR,

    # Source repos / jars. Defaults are the discovered local layout; override per machine.
    [string]$KweebecRoot      = $PSScriptRoot,
    [string]$CommonRoot       = (Join-Path $PSScriptRoot '..\ziggfreed-common'),
    [string]$PerfectUtilsRoot = 'D:/dev/business/hytale-clients/Developer-Utils',

    [string]$OutName  # default: KweebecNightmare-<kweebec version>.jar
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-GradleProp([string]$root, [string]$key) {
    $line = Select-String -Path (Join-Path $root 'gradle.properties') -Pattern "^$key=(.+)$" -ErrorAction SilentlyContinue
    if (-not $line) { return $null }
    return $line.Matches[0].Groups[1].Value.Trim()
}

# --- Resolve source jar paths by each repo's declared version ----------------------
$kwVersion = Get-GradleProp $KweebecRoot 'version'
$zcVersion = Get-GradleProp $CommonRoot 'version'
$puVersion = Get-GradleProp $PerfectUtilsRoot 'version'
$puName    = 'Perfect Utils'   # rootProject.name in Developer-Utils/settings.gradle

$kwJar = Join-Path $KweebecRoot      "build\libs\KweebecNightmare-$kwVersion.jar"
$zcJar = Join-Path $CommonRoot       "build\libs\ZiggfreedCommon-$zcVersion.jar"
$puJar = Join-Path $PerfectUtilsRoot "build\libs\$puName-$puVersion.jar"

if (-not $OutName) { $OutName = "KweebecNightmare-$kwVersion.jar" }
# Distinct output dir so the fat jar never overwrites the plain Kweebec source jar in
# build\libs (which is an INPUT here). The filename stays clean for distribution.
$outDir = Join-Path $KweebecRoot 'build\dist'
$outJar = Join-Path $outDir $OutName
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force $outDir | Out-Null }

# --- Optionally rebuild the three source jars -------------------------------------
if ($Build) {
    Write-Host "`n=== Rebuilding source jars (gradlew build) ===" -ForegroundColor Cyan
    foreach ($r in @($CommonRoot, $PerfectUtilsRoot, $KweebecRoot)) {
        Write-Host "--- $r" -ForegroundColor DarkCyan
        & (Join-Path $r 'gradlew.bat') build
        if ($LASTEXITCODE -ne 0) { throw "gradlew build failed in $r (exit $LASTEXITCODE)" }
    }
}

foreach ($j in @($zcJar, $puJar, $kwJar)) {
    if (-not (Test-Path $j)) { throw "Source jar not found: $j  (run with -Build, or fix the -*Root path)" }
}
Write-Host "`nMerging (top-level identity = KweebecNightmare):" -ForegroundColor Cyan
Write-Host "  $kwJar"
Write-Host "  $zcJar"
Write-Host "  $puJar"

# --- Stage: extract all three jars into one union dir ------------------------------
$staging = Join-Path $KweebecRoot 'build\fatjar-staging'
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force $staging | Out-Null

$manifests = @{}
function Expand-IntoStaging([string]$jarPath, [string]$tag) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        foreach ($entry in $archive.Entries) {
            $name = $entry.FullName
            if ($name.EndsWith('/')) { continue }                              # directory marker
            if ($name -eq 'manifest.json') {                                   # capture, don't extract
                $sr = New-Object System.IO.StreamReader($entry.Open())
                try { $manifests[$tag] = $sr.ReadToEnd() } finally { $sr.Dispose() }
                continue
            }
            if ($name -eq 'META-INF/MANIFEST.MF') { continue }                 # keep only one (added below)
            if ($name -match '^META-INF/.*\.(SF|DSA|RSA)$') { continue }       # drop jar signatures
            $dest = Join-Path $staging ($name -replace '/', '\')
            $destDir = Split-Path $dest -Parent
            if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force $destDir | Out-Null }
            if (Test-Path $dest) {
                $newBytes = New-Object byte[] $entry.Length
                $es = $entry.Open()
                try { [void]$es.Read($newBytes, 0, $entry.Length) } finally { $es.Dispose() }
                $oldBytes = [System.IO.File]::ReadAllBytes($dest)
                if (-not [System.Linq.Enumerable]::SequenceEqual($oldBytes, $newBytes)) {
                    Write-Host "  COLLISION (kept first, ignored $tag): $name" -ForegroundColor Yellow
                }
                continue
            }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
        }
    } finally { $archive.Dispose() }
}

# Order matters only for collision attribution; assets are disjoint in practice.
Expand-IntoStaging $zcJar 'common'
Expand-IntoStaging $puJar 'perfectutils'
Expand-IntoStaging $kwJar 'kweebec'

# --- Compose the combined manifest.json (parent = Common, two SubPlugins) ----------
$common  = $manifests['common']       | ConvertFrom-Json
$perfect = $manifests['perfectutils'] | ConvertFrom-Json
$kweebec = $manifests['kweebec']      | ConvertFrom-Json

# Kweebec is the TOP-LEVEL plugin so the in-game mod list shows it as the primary entry
# (its Name/Description/Authors/Website). Common + Perfect Utils ride along as sub-plugins
# (they still appear as their own library rows). Exactly one asset pack: the parent
# (Kweebec, registered first) owns the merged tree; the subs go Java-only.
$kweebec.IncludesAssetPack = $true
$common.IncludesAssetPack   = $false
$perfect.IncludesAssetPack  = $false

# Drop Kweebec's hard-dep declarations: the subs are guaranteed present in the same jar,
# and keeping them would form a dependency cycle (Kweebec -> {Common,PU} via Dependencies,
# {Common,PU} -> Kweebec via inherit) that aborts load-order resolution. OptionalDependencies
# (MMOSkillTree) stays - it is not a sub-plugin and does not force order.
$kweebec.PSObject.Properties.Remove('Dependencies')

# PluginManifest.inherit() does `this.dependencies.put(parentDep)` on each sub. The
# manifest's Dependencies codec is `unmodifiable`, so ANY present "Dependencies" key -
# empty {} OR populated - decodes to an immutable map (Collections.emptyMap / unmodifiableMap)
# and the put() throws UnsupportedOperationException, aborting boot. ONLY when the key is
# ABSENT does BuilderCodec leave the no-arg constructor's default, a MUTABLE
# Object2ObjectLinkedOpenHashMap, into which inherit() can inject the parent dep. So every
# sub-plugin manifest must OMIT "Dependencies" entirely - inherit() then wires the
# Kweebec-loads-first ordering itself. (Verified against MapCodec.decode + the ctor in the
# live jar; the crash type tracked it exactly: emptyMap -> AbstractMap.put for {},
# UnmodifiableMap.put for a populated map, and NO crash for the sub that omitted the key.)
foreach ($sub in @($common, $perfect)) {
    $sub.PSObject.Properties.Remove('Dependencies')
}

# Nest the two libraries under the parent. inherit() adds a parent dep to each, yielding
# load order Kweebec -> Common -> Perfect Utils (safe: see the header notes).
$kweebec | Add-Member -NotePropertyName 'SubPlugins' -NotePropertyValue @($common, $perfect) -Force

$combinedJson = $kweebec | ConvertTo-Json -Depth 20
# ConvertTo-Json (PS 5.1) may \u-escape < > & in ServerVersion ranges; that is still
# valid JSON and the server's RawJsonReader decodes it back. No fix needed.
$manifestPath = Join-Path $staging 'manifest.json'
[System.IO.File]::WriteAllText($manifestPath, $combinedJson, (New-Object System.Text.UTF8Encoding($false)))

# Minimal jar manifest (Hytale reads manifest.json, not MANIFEST.MF, but keep a valid one).
$metaInf = Join-Path $staging 'META-INF'
if (-not (Test-Path $metaInf)) { New-Item -ItemType Directory -Force $metaInf | Out-Null }
[System.IO.File]::WriteAllText((Join-Path $metaInf 'MANIFEST.MF'),
    "Manifest-Version: 1.0`r`nCreated-By: build-fatjar.ps1`r`n",
    (New-Object System.Text.UTF8Encoding($false)))

# --- Zip the union back into one jar (forward-slash entry names) -------------------
if (Test-Path $outJar) { Remove-Item $outJar -Force }
$zip = [System.IO.Compression.ZipFile]::Open($outJar, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $base = (Resolve-Path $staging).Path.TrimEnd('\') + '\'
    Get-ChildItem -Path $staging -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($base.Length).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $rel)
    }
} finally { $zip.Dispose() }
Remove-Item $staging -Recurse -Force

$sizeKb = [math]::Round((Get-Item $outJar).Length / 1KB)
Write-Host "`nBuilt fat jar: $outJar ($sizeKb KB)" -ForegroundColor Green
Write-Host "Plugins inside: Ziggfreed:KweebecNightmare (parent), Ziggfreed:ZiggfreedCommon, narwhals:Perfect Utils" -ForegroundColor Green

# --- Optional install -------------------------------------------------------------
if (-not $Install) { return }
if (-not $ModsDir) {
    Write-Host "No Mods folder set - pass -ModsDir <path> or set `$env:HYTALE_MODS_DIR to install." -ForegroundColor Yellow
    return
}
if (-not (Test-Path $ModsDir)) { throw "Mods folder does not exist: $ModsDir" }

# CRITICAL: a standalone ZiggfreedCommon / Perfect Utils / KweebecNightmare jar still in
# Mods/ would register a DUPLICATE plugin id (the engine then shuts the server down).
# Remove every old runtime jar for all three before dropping the single fat jar in.
$drop = @('ZiggfreedCommon-*.jar', 'Perfect Utils-*.jar', 'KweebecNightmare-*.jar')
foreach ($glob in $drop) {
    Get-ChildItem -Path $ModsDir -Filter $glob -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        ForEach-Object { Write-Host "  removing old: $($_.Name)" -ForegroundColor DarkYellow; Remove-Item $_.FullName -Force }
}
Copy-Item $outJar (Join-Path $ModsDir $OutName) -Force
Write-Host "Installed fat jar to $(Join-Path $ModsDir $OutName)" -ForegroundColor Green
