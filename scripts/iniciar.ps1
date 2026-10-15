param(
    [string]$Config = '.\config.json',
    [int]$Puerto = 8080
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$raizProyecto = Split-Path -Parent $PSScriptRoot

Push-Location -LiteralPath $raizProyecto
try {
    if (-not (Test-Path -LiteralPath 'build/gestor-procesos.jar' -PathType Leaf)) {
        & (Join-Path $PSScriptRoot 'compilar.ps1')
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }

    & java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar 'build/gestor-procesos.jar' --config $Config --puerto $Puerto
    exit $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine("Error al iniciar el simulador: $($_.Exception.Message)")
    exit 1
} finally {
    Pop-Location
}
