# Compatible con Windows PowerShell 5.1 y PowerShell 7; sin módulos externos.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$raizProyecto = Split-Path -Parent $PSScriptRoot
$rutaCli = Join-Path $raizProyecto 'gestor.ps1'
$interprete = (Get-Process -Id $PID).Path
$servidor = $null
$fallidas = 0
$ejecutadas = 0
$duraciones = New-Object 'System.Collections.Generic.List[double]'
$urlServidor = 'http://127.0.0.1:18080'
$opcionesLocales = @{}
$proxyAnterior = [Net.WebRequest]::DefaultWebProxy
if ($PSVersionTable.PSVersion.Major -ge 6) { $opcionesLocales.NoProxy = $true }
else { [Net.WebRequest]::DefaultWebProxy = [Net.WebProxy]::new() }

function Afirmar-Condicion([bool]$Condicion, [string]$Mensaje) {
    if (-not $Condicion) { throw $Mensaje }
}

function Probar-Caso([string]$Nombre, [scriptblock]$Accion) {
    $script:ejecutadas++
    try {
        & $Accion
        Write-Output "[OK] $Nombre"
    } catch {
        $script:fallidas++
        Write-Output "[FALLO] ${Nombre}: $($_.Exception.Message)"
    }
}

function Consultar-Cli([string[]]$ArgumentosCli) {
    $opciones = New-Object System.Diagnostics.ProcessStartInfo
    $opciones.FileName = $interprete
    $opciones.Arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $rutaCli + '" ' + ($ArgumentosCli -join ' ')
    $opciones.UseShellExecute = $false
    $opciones.CreateNoWindow = $true
    $opciones.RedirectStandardOutput = $true
    $opciones.RedirectStandardError = $true
    $opciones.StandardOutputEncoding = [Text.Encoding]::UTF8
    $opciones.StandardErrorEncoding = [Text.Encoding]::UTF8
    $procesoCli = New-Object System.Diagnostics.Process
    $procesoCli.StartInfo = $opciones
    try {
        $medicion = Measure-Command {
            $null = $procesoCli.Start()
            $salidaPendiente = $procesoCli.StandardOutput.ReadToEndAsync()
            $erroresPendientes = $procesoCli.StandardError.ReadToEndAsync()
            if (-not $procesoCli.WaitForExit(6000)) { throw 'La CLI no terminó dentro de seis segundos' }
            $salidaCli = $salidaPendiente.GetAwaiter().GetResult()
            $erroresCli = $erroresPendientes.GetAwaiter().GetResult()
        }
        return [pscustomobject]@{ Codigo = $procesoCli.ExitCode; Salida = $salidaCli; Errores = $erroresCli; Segundos = $medicion.TotalSeconds }
    } finally {
        if ($procesoCli.Id -and -not $procesoCli.HasExited) { $procesoCli.Kill(); $procesoCli.WaitForExit() }
        $procesoCli.Dispose()
    }
}

function Esperar-Estado([scriptblock]$Condicion) {
    $limite = [DateTime]::UtcNow.AddSeconds(10)
    do {
        $estado = Invoke-RestMethod -Uri "$urlServidor/api/estado" -TimeoutSec 2 @opcionesLocales
        if (& $Condicion $estado) { return $estado }
        Start-Sleep -Milliseconds 30
    } while ([DateTime]::UtcNow -lt $limite)
    throw 'El simulador no alcanzó el estado esperado dentro de diez segundos'
}

Push-Location -LiteralPath $raizProyecto
try {
    & (Join-Path $PSScriptRoot 'compilar.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el simulador' }
    if (-not (Test-Path -LiteralPath $rutaCli -PathType Leaf)) { throw 'Falta gestor.ps1' }
    # No se debe consultar ni detener un servidor ajeno que ya use el puerto de esta batería.
    $reserva = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 18080)
    try { $reserva.Start() } finally { $reserva.Stop() }
    $servidor = Start-Process -FilePath java -ArgumentList @('-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', 'build/gestor-procesos.jar', '--config', 'ejemplos/referencia-rr.json', '--puerto', '18080') -WindowStyle Hidden -PassThru -RedirectStandardOutput 'build/cli-servidor-salida.log' -RedirectStandardError 'build/cli-servidor-errores.log'
    $limite = [DateTime]::UtcNow.AddSeconds(10)
    $disponible = $false
    do {
        if ($servidor.HasExited) { throw 'El servidor terminó durante el arranque; consultar build/cli-servidor-errores.log' }
        try {
            $null = Invoke-RestMethod -Uri "$urlServidor/api/estado" -TimeoutSec 2 @opcionesLocales
            $disponible = $true
        } catch { $ultimoErrorArranque = $_.Exception.Message; Start-Sleep -Milliseconds 30 }
    } while (-not $disponible -and [DateTime]::UtcNow -lt $limite)
    Afirmar-Condicion $disponible "El servidor no respondió dentro de diez segundos: $ultimoErrorArranque"

    Probar-Caso '1. Sin argumento: uso y código 1' {
        $resultado = Consultar-Cli @('-Puerto', '18080')
        Afirmar-Condicion ($resultado.Codigo -eq 1 -and $resultado.Errores.Contains('Uso: .\gestor.ps1 <idproceso> [-Puerto 8080]')) 'Se esperaba uso en stderr y código 1'
    }
    Probar-Caso '2. Proceso inexistente: código 2' {
        $resultado = Consultar-Cli @('P9', '-Puerto', '18080')
        $duraciones.Add($resultado.Segundos)
        Afirmar-Condicion ($resultado.Codigo -eq 2 -and $resultado.Errores.Contains("Error: el proceso 'P9' no existe en la simulación.")) 'Mensaje o código incorrecto para P9'
    }
    Probar-Caso '3. Pendiente de llegada: código 0' {
        $resultado = Consultar-Cli @('P2', '-Puerto', '18080')
        $duraciones.Add($resultado.Segundos)
        Afirmar-Condicion ($resultado.Codigo -eq 0 -and $resultado.Salida.Contains("El proceso 'P2' todavía no ha llegado al sistema (llega en el instante 1).") -and $resultado.Errores.Length -eq 0) 'Pendiente de llegada incorrecto'
    }
    $null = Invoke-RestMethod -Uri "$urlServidor/api/configuracion" -Method Post -ContentType 'application/json; charset=utf-8' -Body '{"velocidadMs":2000}' -TimeoutSec 2 @opcionesLocales
    $null = Invoke-RestMethod -Uri "$urlServidor/api/control/iniciar" -Method Post -TimeoutSec 2 @opcionesLocales
    $null = Esperar-Estado { param($estado) $estado.instante -ge 2 }
    $pausa = Invoke-RestMethod -Uri "$urlServidor/api/control/pausar" -Method Post -TimeoutSec 2 @opcionesLocales
    Probar-Caso '4. Proceso activo: ficha completa y código 0' {
        $resultado = Consultar-Cli @('P1', '-Puerto', '18080')
        $duraciones.Add($resultado.Segundos)
        Afirmar-Condicion ($resultado.Codigo -eq 0 -and $resultado.Errores.Length -eq 0) 'La ficha no terminó correctamente'
        foreach ($etiqueta in @('Proceso P1', 'Estado ..................:', 'Quantums (ticks) consumidos :', 'Quantums (ticks) restantes  :', 'Bloque de RAM ...........:', 'Tiempo de espera ........:', 'Adelantamientos .........:')) {
            Afirmar-Condicion ($resultado.Salida.Contains($etiqueta)) "Falta la etiqueta $etiqueta"
        }
        Afirmar-Condicion ($resultado.Salida.Contains("(instante actual: $($pausa.instante))")) 'El reloj de CLI no coincide con la API pausada'
        Afirmar-Condicion ($resultado.Salida.Contains('[0 – 29] (30 MB)')) 'El bloque de P1 no coincide con la API'
    }
    $null = Invoke-RestMethod -Uri "$urlServidor/api/configuracion" -Method Post -ContentType 'application/json; charset=utf-8' -Body '{"velocidadMs":50}' -TimeoutSec 2 @opcionesLocales
    $null = Invoke-RestMethod -Uri "$urlServidor/api/control/iniciar" -Method Post -TimeoutSec 2 @opcionesLocales
    $null = Esperar-Estado { param($estado) $estado.modo -eq 'FINALIZADA' }
    Probar-Caso '5. Proceso terminado: código 3' {
        $resultado = Consultar-Cli @('P1', '-Puerto', '18080')
        $duraciones.Add($resultado.Segundos)
        Afirmar-Condicion ($resultado.Codigo -eq 3 -and $resultado.Errores.Contains("Error: el proceso 'P1' ya finalizó su ciclo de vida (terminó en el instante 6).")) 'Finalización incorrecta'
    }
    Probar-Caso '6. SC-005: consultas de los casos 2 a 5 en menos de 2 s' {
        Afirmar-Condicion ($duraciones.Count -eq 4) 'Faltan mediciones de CLI'
        foreach ($segundos in $duraciones) { Afirmar-Condicion ($segundos -lt 2) ("Consulta demasiado lenta: {0:F3} s" -f $segundos) }
        Write-Output ('    Duraciones: ' + (($duraciones | ForEach-Object { '{0:F3} s' -f $_ }) -join ', '))
    }
    Stop-Process -Id $servidor.Id -Force
    $servidor.WaitForExit()
    Probar-Caso '7. Sin servidor: código 4' {
        $resultado = Consultar-Cli @('P1', '-Puerto', '18080')
        Afirmar-Condicion ($resultado.Codigo -eq 4 -and $resultado.Errores.Contains('Error: no se pudo conectar con el simulador en http://localhost:18080. ¿Está en ejecución?')) 'Conexión fallida incorrecta'
    }
} catch {
    $fallidas++
    [Console]::Error.WriteLine("Error en la batería CLI: $($_.Exception.Message)")
} finally {
    if ($null -ne $servidor) {
        if (-not $servidor.HasExited) { Stop-Process -Id $servidor.Id -Force; $servidor.WaitForExit() }
        $servidor.Dispose()
    }
    Pop-Location
    if ($PSVersionTable.PSVersion.Major -lt 6) { [Net.WebRequest]::DefaultWebProxy = $proxyAnterior }
}
Write-Output "$ejecutadas pruebas CLI, $fallidas fallidas"
if ($fallidas -gt 0) { exit 1 }
exit 0
