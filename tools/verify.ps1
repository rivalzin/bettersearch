param(
    [switch]$CoreOnly,
    [string]$Python = 'python',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments
)

$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $taskRoot
try {
    & $Python tools/source_audit.py
    if ($LASTEXITCODE -ne 0) { throw 'A verificacao estrutural falhou.' }
    if ($CoreOnly) {
        & .\gradlew.bat '-PcoreOnly=true' check @GradleArguments
    } else {
        & .\gradlew.bat dist @GradleArguments
    }
    if ($LASTEXITCODE -ne 0) { throw 'A compilacao ou os testes falharam.' }
    if (-not $CoreOnly) {
        & $Python tools/source_audit.py --jars
        if ($LASTEXITCODE -ne 0) { throw 'A verificacao dos jars falhou.' }
    }
} finally {
    Pop-Location
}
