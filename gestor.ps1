param(
    [Parameter(Position = 0)][string]$IdProceso,
    [int]$Puerto = 8080
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if ([string]::IsNullOrWhiteSpace($IdProceso)) {
    [Console]::Error.WriteLine('Uso: .\gestor.ps1 <idproceso> [-Puerto 8080]')
    exit 1
}
$urlServidor = "http://localhost:$Puerto"
# El HttpServer del JDK usa loopback IPv4 por defecto. Evitar la espera de resolución IPv6
# mantiene SC-005 incluso si Windows resuelve primero ::1 sin un servidor en esa dirección.
$urlPeticion = "http://127.0.0.1:$Puerto"
$idCodificado = [Uri]::EscapeDataString($IdProceso)
$opcionesLocales = @{}
$proxyAnterior = [Net.WebRequest]::DefaultWebProxy
if ($PSVersionTable.PSVersion.Major -ge 6) {
    $opcionesLocales.NoProxy = $true
} else {
    # El servicio es local: no debe atravesar el proxy configurado para Internet.
    [Net.WebRequest]::DefaultWebProxy = [Net.WebProxy]::new()
}
try {
    $respuesta = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Uri "$urlPeticion/api/proceso/$idCodificado" @opcionesLocales
} catch {
    $respuestaError = $_.Exception.Response
    if ($null -eq $respuestaError) {
        [Console]::Error.WriteLine("Error: no se pudo conectar con el simulador en $urlServidor. ¿Está en ejecución?")
        exit 4
    }
    $codigoHttp = [int]$respuestaError.StatusCode
    # PS 5.1 ofrece HttpWebResponse; PS 7, HttpResponseMessage. Ambos se leen como UTF-8.
    try {
        if ($respuestaError.PSObject.Methods['GetResponseStream']) {
            $lectorError = [IO.StreamReader]::new($respuestaError.GetResponseStream(), [Text.Encoding]::UTF8)
            try { $cuerpoError = $lectorError.ReadToEnd() } finally { $lectorError.Dispose() }
        } else {
            $bytesError = $respuestaError.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $cuerpoError = [Text.Encoding]::UTF8.GetString($bytesError)
        }
        $null = $cuerpoError | ConvertFrom-Json
    } catch {
        # El código HTTP sigue siendo utilizable si falla la lectura del cuerpo de error.
    }
    if ($codigoHttp -eq 404) {
        [Console]::Error.WriteLine("Error: el proceso '$IdProceso' no existe en la simulación.")
        exit 2
    }
    [Console]::Error.WriteLine("Error: respuesta inesperada del simulador ($codigoHttp).")
    exit 5
} finally {
    if ($PSVersionTable.PSVersion.Major -lt 6) { [Net.WebRequest]::DefaultWebProxy = $proxyAnterior }
}
try {
    $codigoHttp = [int]$respuesta.StatusCode
    if ($codigoHttp -ne 200) { throw 'Código HTTP inesperado' }
    $lector = [IO.StreamReader]::new($respuesta.RawContentStream, [Text.Encoding]::UTF8)
    try { $proceso = $lector.ReadToEnd() | ConvertFrom-Json } finally { $lector.Dispose() }
    $etiquetasEstado = @{
        NUEVO = 'Nuevo / Espera de memoria'
        LISTO = 'Listo'
        EJECUCION = 'En Ejecución'
        BLOQUEADO = 'Bloqueado'
        TERMINADO = 'Terminado'
        PENDIENTE_LLEGADA = 'Pendiente de llegada'
    }
    if ($null -eq $proceso -or -not $etiquetasEstado.ContainsKey([string]$proceso.estado)) { throw 'Estado inesperado' }
} catch {
    [Console]::Error.WriteLine("Error: respuesta inesperada del simulador ($codigoHttp).")
    exit 5
}
if ($proceso.estado -eq 'PENDIENTE_LLEGADA') {
    Write-Output "El proceso '$IdProceso' todavía no ha llegado al sistema (llega en el instante $($proceso.tiempoLlegada))."
    exit 0
}
if ($proceso.estado -eq 'TERMINADO') {
    [Console]::Error.WriteLine("Error: el proceso '$IdProceso' ya finalizó su ciclo de vida (terminó en el instante $($proceso.instanteFinalizacion)).")
    exit 3
}
$bloque = 'sin asignar (esperando memoria)'
if ($null -ne $proceso.bloque) { $bloque = "[$($proceso.bloque.base) – $($proceso.bloque.limite)] ($($proceso.bloque.tamano) MB)" }
Write-Output "Proceso $IdProceso  (instante actual: $($proceso.instante))"
Write-Output "  Estado ..................: $($etiquetasEstado[[string]$proceso.estado])"
Write-Output "  Quantums (ticks) consumidos : $($proceso.ticksConsumidos)"
Write-Output "  Quantums (ticks) restantes  : $($proceso.ticksRestantes)"
Write-Output "  Bloque de RAM ...........: $bloque"
Write-Output "  Tiempo de espera ........: $($proceso.tiempoEspera) ticks"
Write-Output "  Adelantamientos .........: $($proceso.adelantamientos)"
if ($proceso.bloqueante) { Write-Output '  Prioridad de admisión : sí (bloqueante)' }
if ($proceso.estado -eq 'BLOQUEADO') { Write-Output "  E/S restante ............: $($proceso.ticksRestantesES) ticks" }
exit 0
