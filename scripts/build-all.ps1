[CmdletBinding()]
param(
    [switch]$PublishToMavenLocal,
    [switch]$ListOnly,
    [string]$LocalInputsRoot,
    [string[]]$Branches = @(
        "forge/16.5",
        "forge/18.2",
        "forge/19.2",
        "forge/20.1",
        "forge/21.1",
        "fabric/16.5",
        "fabric/18.2",
        "fabric/19.2",
        "fabric/20.1",
        "fabric/21.1",
        "neoforge/21.1"
    )
)

$ErrorActionPreference = "Stop"
$docsRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$repoRoot = (& git -C $docsRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    throw "Unable to locate the repository from $docsRoot"
}

function Assert-NativeSuccess {
    param(
        [string]$Description,
        [int]$ExitCode
    )

    if ($exitCode -ne 0) {
        throw "$Description failed with exit code $ExitCode"
    }
}

function Get-BranchFile {
    param(
        [string]$Branch,
        [string]$Path
    )

    $content = @(& git -C $repoRoot show "${Branch}:${Path}")
    Assert-NativeSuccess "git show ${Branch}:${Path}" $LASTEXITCODE
    return $content -join [Environment]::NewLine
}

function Get-JavaVersion {
    param([string]$Branch)

    $buildScript = Get-BranchFile $Branch "build.gradle"
    $match = [regex]::Match($buildScript, "(?m)^\s*def\s+javaVer\s*=\s*(\d+)\s*$")
    if (-not $match.Success) {
        throw "Unable to read javaVer from ${Branch}:build.gradle"
    }
    return $match.Groups[1].Value
}

function Get-JavaHome {
    param(
        [string]$Branch,
        [string]$JavaVersion
    )

    $jdkProperties = Get-BranchFile $Branch "jdks.properties"
    foreach ($line in $jdkProperties -split "\r?\n") {
        if ($line -match "^\s*jdk$([regex]::Escape($JavaVersion))\s*=\s*(.+?)\s*$") {
            return $matches[1]
        }
    }
    throw "Unable to find jdk${JavaVersion} in ${Branch}:jdks.properties"
}

function Find-LocalInputsRoot {
    if (-not [string]::IsNullOrWhiteSpace($LocalInputsRoot)) {
        $resolved = [IO.Path]::GetFullPath($LocalInputsRoot)
        if (-not (Test-Path -LiteralPath $resolved -PathType Container)) {
            throw "Local inputs root does not exist: $resolved"
        }
        return $resolved
    }

    # Ignored local jars are not copied by git worktree, so reuse them from an existing checkout.
    $worktreeLines = @(& git -C $repoRoot worktree list --porcelain)
    Assert-NativeSuccess "git worktree list" $LASTEXITCODE
    foreach ($line in $worktreeLines) {
        if ($line -match "^worktree\s+(.+)$") {
            $candidate = $matches[1]
            if (Test-Path -LiteralPath (Join-Path $candidate "libs") -PathType Container) {
                return $candidate
            }
        }
    }
    return $null
}

$worktreeBase = Join-Path (Split-Path $repoRoot -Parent) (
    ".docs-build-" + ([IO.Path]::GetFileName($repoRoot) -replace "[^A-Za-z0-9._-]", "_")
)
$localInputs = Find-LocalInputsRoot
$tasks = @("clean", "test", "assemble")
if ($PublishToMavenLocal) {
    $tasks += "publishToMavenLocal"
}

Write-Host "Repository: $repoRoot"
Write-Host "Tasks: $($tasks -join ' ')"
Write-Host "Branches: $($Branches -join ', ')"

foreach ($branch in $Branches) {
    $commitOutput = @(& git -C $repoRoot rev-parse --verify "refs/heads/$branch")
    Assert-NativeSuccess "git rev-parse refs/heads/$branch" $LASTEXITCODE
    if ($commitOutput.Count -eq 0) {
        throw "git rev-parse returned no commit for $branch"
    }
    $commit = $commitOutput[0].Trim()
    $javaVersion = Get-JavaVersion $branch
    $javaHome = Get-JavaHome $branch $javaVersion
    Write-Host "[$branch] $commit, Java $javaVersion ($javaHome)"

    if ($ListOnly) {
        continue
    }

    if (Test-Path -LiteralPath $worktreeBase) {
        throw "Temporary worktree path already exists: $worktreeBase"
    }

    $worktreeAdded = $false
    try {
        & git -C $repoRoot worktree add --detach $worktreeBase $commit | Out-Null
        Assert-NativeSuccess "git worktree add $branch" $LASTEXITCODE
        $worktreeAdded = $true

        if ($null -ne $localInputs) {
            Copy-Item -LiteralPath (Join-Path $localInputs "libs") `
                -Destination (Join-Path $worktreeBase "libs") -Recurse -Force
        }

        $previousJavaHome = $env:JAVA_HOME
        $previousPath = $env:Path
        try {
            $env:JAVA_HOME = $javaHome
            $env:Path = "$javaHome\bin;$previousPath"
            Push-Location $worktreeBase
            try {
                & ".\gradlew.bat" @tasks "--no-daemon" "--console=plain"
                if ($LASTEXITCODE -ne 0) {
                    throw "Gradle failed for $branch with exit code $LASTEXITCODE"
                }
            } finally {
                Pop-Location
            }
        } finally {
            $env:JAVA_HOME = $previousJavaHome
            $env:Path = $previousPath
        }
    } finally {
        if ($worktreeAdded) {
            & git -C $repoRoot worktree remove --force $worktreeBase | Out-Null
            Assert-NativeSuccess "git worktree remove $branch" $LASTEXITCODE
        }
        & git -C $repoRoot worktree prune | Out-Null
        Assert-NativeSuccess "git worktree prune" $LASTEXITCODE
    }
}

Write-Host "All requested branches completed."
