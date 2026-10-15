$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$raizProyecto = Split-Path -Parent $PSScriptRoot

Push-Location -LiteralPath $raizProyecto
try {
    & (Join-Path $PSScriptRoot 'compilar.ps1')
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    if (Test-Path -LiteralPath 'build/pruebas') {
        Remove-Item -LiteralPath 'build/pruebas' -Recurse -Force
    }
    New-Item -ItemType Directory -Path 'build/pruebas' -Force | Out-Null

    $raizPruebas = (Resolve-Path -LiteralPath 'src/test/java').Path
    $archivosJava = @(Get-ChildItem -LiteralPath $raizPruebas -Recurse -Filter '*.java' -File |
        Sort-Object FullName)
    $rutasJava = @($archivosJava | ForEach-Object { $_.FullName })
    & javac --release 21 -encoding UTF-8 -cp 'build/clases' -d 'build/pruebas' $rutasJava
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine('Error: no se pudieron compilar las pruebas.')
        exit 1
    }

    Copy-Item -LiteralPath 'src/test/recursos' -Destination 'build/pruebas/recursos' -Recurse -Force
    $clasesPrueba = @($archivosJava | Where-Object { $_.Name -like '*Prueba.java' } |
        ForEach-Object {
            $rutaRelativa = $_.FullName.Substring($raizPruebas.Length + 1)
            ($rutaRelativa -replace '\.java$', '') -replace '[\\/]', '.'
        })

    & java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp 'build/clases;build/pruebas' gestor.pruebas.EjecutorPruebas @clasesPrueba
    exit $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine("Error al ejecutar las pruebas: $($_.Exception.Message)")
    exit 1
} finally {
    Pop-Location
}
