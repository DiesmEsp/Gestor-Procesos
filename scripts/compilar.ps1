$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$raizProyecto = Split-Path -Parent $PSScriptRoot

Push-Location -LiteralPath $raizProyecto
try {
    if (Test-Path -LiteralPath 'build/clases') {
        Remove-Item -LiteralPath 'build/clases' -Recurse -Force
    }
    New-Item -ItemType Directory -Path 'build/clases' -Force | Out-Null

    $archivosJava = @(Get-ChildItem -LiteralPath 'src/main/java' -Recurse -Filter '*.java' -File |
        Sort-Object FullName | ForEach-Object { $_.FullName })

    # El primer lote permite compilar sin clases de producción todavía.
    if ($archivosJava.Count -gt 0) {
        & javac --release 21 -encoding UTF-8 -d 'build/clases' $archivosJava
        if ($LASTEXITCODE -ne 0) {
            [Console]::Error.WriteLine('Error: no se pudieron compilar las clases de producción.')
            exit 1
        }
    }

    Copy-Item -Path 'src/main/resources/*' -Destination 'build/clases' -Recurse -Force
    & jar --create --file 'build/gestor-procesos.jar' --main-class gestor.Principal -C 'build/clases' .
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine('Error: no se pudo generar el archivo JAR.')
        exit 1
    }
    exit 0
} catch {
    [Console]::Error.WriteLine("Error al compilar el proyecto: $($_.Exception.Message)")
    exit 1
} finally {
    Pop-Location
}
