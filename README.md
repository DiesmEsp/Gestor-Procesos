# Gestor-Procesos

Simulador educativo de gestión de procesos y memoria RAM para Sistemas Operativos: planificación
Round Robin con E/S simulada, memoria de partición variable con Best-Fit, dashboard web en tiempo
real y consulta de procesos desde PowerShell. Está hecho con Java 21 y solo la biblioteca estándar.

> **Estado**: infraestructura, núcleo de US1/US2, carga JSON de US5 y backend HTTP de US3
> completados (T001–T055). `.\scripts\probar.ps1` reporta `152 pruebas, 0 fallidas`.
> `.\scripts\iniciar.ps1` carga `config.json` y publica la URL local del servidor.
> La página inicial confirma disponibilidad; el dashboard (T056–T058) sigue pendiente. Ver
> [HANDOFF.md](HANDOFF.md).

```powershell
.\scripts\iniciar.ps1 -Config .\ejemplos\referencia-rr.json -Puerto 8080
```

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
