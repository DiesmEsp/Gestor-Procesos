# Guía rápida de validación: Simulador de Gestión de Procesos y Memoria RAM

## Requisitos

- Windows 10/11 con PowerShell 5.1 o 7.
- JDK 21 en el `PATH` (`java -version` muestra `21.x`). No se necesita nada más.

## 1. Compilar y probar

```powershell
.\scripts\compilar.ps1     # genera build\gestor-procesos.jar
.\scripts\probar.ps1       # compila y ejecuta todas las clases *Prueba
```

Esperado: `probar.ps1` lista cada prueba con `✔`/`✘`, termina con `N pruebas, 0 fallidas` y código 0.
Las pruebas incluyen los escenarios de referencia de la spec (Historia 1, escenarios 1–2;
Historia 2, escenarios 1–7) y la batería de archivos inválidos (`src/test/recursos/invalidos/`).

## 2. Ejecutar un escenario de referencia

```powershell
.\scripts\iniciar.ps1 -Config .\ejemplos\referencia-rr.json   # RAM 100, quantum 2, P1 y P2
```

Abrir `http://localhost:8080`, pulsar **Iniciar**. Esperado al finalizar (instante 7):

- Gantt `P1[0–2) P2[2–4) P1[4–6) P2[6–7)`.
- Métricas: espera promedio 2,50; retorno promedio 6,00; CPU 100,0 %.

Otros escenarios en `ejemplos/`: `referencia-es.json` (E/S, CPU 60,0 %), `best-fit.json`
(hueco elegido [70–94]), `first-fit.json` (mismo escenario con First-Fit: bloque [0–24]), `inanicion.json` (proceso bloqueante tras 5 adelantamientos),
`config.json` (escenario didáctico general).

## 3. Controles del dashboard

| Acción | Resultado esperado |
|---|---|
| Pausar y luego Iniciar/Reanudar | el instante continúa sin saltos ni repeticiones |
| Mover el control de velocidad a 50 ms | la vista sigue cada tick; el resultado final no cambia |
| Cambiar quantum a 3 en curso | los siguientes despachos usan 3 ticks |
| Cambiar RAM en curso | rechazado: "La capacidad de RAM solo puede modificarse antes de iniciar" |
| Cambiar la política a First-Fit en el instante 0 con `best-fit.json` e iniciar | el proceso de 25 MB recibe [0–24] |
| Cambiar la política en curso | rechazado, igual que la RAM |
| Reiniciar | instante 0, detenida, Gantt y bitácora vacíos, quantum/RAM/política elegidos conservados |

## 4. CLI

Con el simulador en ejecución, en otra consola (ver `contracts/cli-gestor.md`):

```powershell
.\gestor.ps1 P2        # ficha del proceso, código 0
.\gestor.ps1 P99       # "no existe", código 2
.\gestor.ps1 P1        # tras terminar: "ya finalizó…", código 3
```

Con el simulador apagado: mensaje de conexión y código 4. Ver el código con `$LASTEXITCODE`.

## 5. Validación de configuración

```powershell
.\scripts\iniciar.ps1 -Config .\src\test\recursos\invalidos\id-duplicado.json
```

Esperado: mensaje `Error en config.json: … el id "P1" está duplicado`, código 1, sin servidor.
