param(
    [string]$RaizFuentes = (Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/java/gestor')
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
try {
    $raiz = (Resolve-Path -LiteralPath $RaizFuentes).Path
    $archivos = @(Get-ChildItem -LiteralPath $raiz -Recurse -Filter '*.java' -File | Sort-Object FullName)
    if ($archivos.Count -eq 0) { throw 'No se encontraron fuentes Java para verificar' }
    $errores = New-Object 'System.Collections.Generic.List[string]'
    foreach ($archivo in $archivos) {
        $relativa = $archivo.FullName.Substring($raiz.Length + 1).Replace('\', '/')
        $capa = ($relativa -split '/')[0]
        $texto = [IO.File]::ReadAllText($archivo.FullName)
        # Quitar comentarios y literales conservando los saltos de línea para informar ubicaciones.
        $patronLiterales = '(?s)""".*?"""|"(?:\\.|[^"\\])*"|''(?:\\.|[^''\\])*''|/\*.*?\*/|//[^\r\n]*'
        $sinComentarios = [regex]::Replace($texto, $patronLiterales, { param($coincidencia) [regex]::Replace($coincidencia.Value, '[^\r\n]', ' ') })
        $importaciones = [regex]::Matches($sinComentarios, '(?m)^[ \t]*import\s+(?:static\s+)?([\w$]+(?:\s*\.\s*(?:[\w$]+|\*))+)[ \t\r\n]*;')
        foreach ($importacion in $importaciones) {
            $destino = $importacion.Groups[1].Value -replace '\s', ''
            $motivo = $null
            if ($destino -notmatch '^(java\.|javax\.|com\.sun\.net\.httpserver\.|gestor\.)') {
                $motivo = 'dependencia externa no permitida'
            } elseif ($destino -match '^gestor\.entrada(?:\.|$)' -and $relativa -ne 'Principal.java') {
                $motivo = 'solo Principal puede importar gestor.entrada'
            } elseif ($capa -in @('modelo', 'recursos', 'motor') -and $destino -match '^(gestor\.(entrada|json|interfaz)(\.|$)|com\.sun\.net\.httpserver(\.|$)|java\.(net|io)(\.|$)|java\.nio\.file(\.|$))') {
                $motivo = 'el núcleo no puede depender de entrada, JSON, interfaces ni E/S'
            } elseif ($capa -eq 'recursos' -and $destino -match '^gestor\.motor(?:\.|$)') {
                $motivo = 'recursos no puede depender del motor'
            } elseif ($destino -match '^gestor\.([^.]+)\.') {
                $capaDestino = $Matches[1]
                $permitidas = switch ($capa) {
                    'modelo' { @('modelo') }
                    'json' { @('json') }
                    'recursos' { @('recursos', 'modelo') }
                    'motor' { @('motor', 'recursos', 'modelo') }
                    'entrada' { @('entrada', 'modelo', 'json') }
                    'interfaz' { @('interfaz', 'motor', 'modelo', 'json') }
                    default { @('modelo', 'json', 'recursos', 'motor', 'entrada', 'interfaz') }
                }
                if ($capaDestino -notin $permitidas) { $motivo = "dependencia de $capa hacia $capaDestino no permitida por plan.md" }
            }
            if ($motivo) {
                $linea = 1 + ([regex]::Matches($sinComentarios.Substring(0, $importacion.Index), '\n')).Count
                $errores.Add("${relativa}:${linea}: $destino — $motivo")
            }
        }
    }
    if ($errores.Count -gt 0) {
        foreach ($errorCapa in $errores) { [Console]::Error.WriteLine("Error de capas: $errorCapa") }
        exit 1
    }
    Write-Output "[OK] Capas: $($archivos.Count) archivos Java, sin dependencias prohibidas"
    exit 0
} catch {
    [Console]::Error.WriteLine("Error al verificar las capas: $($_.Exception.Message)")
    exit 1
}
