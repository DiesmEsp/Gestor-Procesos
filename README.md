# Gestor-Procesos

Simulador educativo de gestión de procesos y memoria RAM para Sistemas Operativos: planificación
Round Robin con E/S simulada, memoria de partición variable con Best-Fit, dashboard web en tiempo
real y consulta de procesos desde PowerShell. Está hecho con Java 21 y solo la biblioteca estándar.

> **Estado**: núcleo, carga JSON, dashboard web y CLI completados (T001–T060).
> `.\scripts\probar.ps1` reporta `152 pruebas, 0 fallidas`.
> `.\scripts\probar-cli.ps1` reporta `7 pruebas CLI, 0 fallidas` en PowerShell 5.1 y 7.
> `.\scripts\iniciar.ps1` carga `config.json` y publica la URL local del servidor.
> Abrir esa URL para controlar y observar la simulación. Siguiente fase: pulido T061–T065.
> Ver [HANDOFF.md](HANDOFF.md).

```powershell
.\scripts\iniciar.ps1 -Config .\ejemplos\referencia-rr.json -Puerto 8080
```

El dashboard muestra las colas, RAM, Gantt, métricas y bitácora. Se pueden modificar quantum y
velocidad; RAM y política solo antes de iniciar o después de reiniciar. Los promedios se
calculan sobre procesos terminados. Los cambios de velocidad y las pausas conservan el
resultado lógico.

En otra consola, consultar un proceso o verificar la CLI:

```powershell
.\gestor.ps1 P2 -Puerto 8080
.\scripts\probar-cli.ps1
```

La CLI informa procesos pendientes y muestra la ficha de los activos. Sus códigos son:
0 consulta correcta, 1 falta argumento, 2 proceso inexistente, 3 terminado, 4 sin conexión,
5 respuesta inesperada. Ver [el contrato CLI](specs/001-simulador-procesos-memoria/contracts/cli-gestor.md).
La batería reserva el puerto 18080 y detiene siempre su proceso Java. Los scripts de CLI
están en UTF-8 con BOM y usan loopback IPv4 sin proxy para consultas locales rápidas.

Con el servidor activo en otra consola:

```powershell
Invoke-RestMethod http://localhost:8080/api/estado
Invoke-RestMethod http://localhost:8080/api/control/iniciar -Method Post
Invoke-RestMethod http://localhost:8080/api/control/pausar -Method Post
Invoke-RestMethod http://localhost:8080/api/control/reiniciar -Method Post
```

El servidor solo escucha en loopback. Reiniciar relee el archivo y conserva los valores de
quantum, RAM y política modificados desde la API. Los detalles de rutas, parámetros y errores
están en [el contrato HTTP](specs/001-simulador-procesos-memoria/contracts/api-http.md).
