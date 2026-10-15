# Implementation Plan: Simulador de Gestión de Procesos y Memoria RAM

**Branch**: `001-simulador-procesos-memoria` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-simulador-procesos-memoria/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Simulador educativo que carga procesos desde `config.json`, los ejecuta con un motor de ticks
discretos y deterministas (Round Robin expulsivo con E/S simulada) sobre una RAM de partición
variable con Best-Fit o First-Fit seleccionable, fusión de huecos y límite de 5 adelantamientos en
la espera de memoria.
Se expone por un dashboard web (HTML/CSS/JS Vanilla servido por `HttpServer` del JDK con sondeo
JSON) y por un script `gestor.ps1` que consulta la API local. Todo con Java 21 y la biblioteca
estándar; construcción con scripts PowerShell (`javac`/`jar`) y micro-framework de pruebas propio.

## Technical Context

**Language/Version**: Java 21 (LTS) — JDK 21.0.10 verificado; PowerShell 5.1/7 para scripts y CLI;
HTML5 + CSS + JavaScript Vanilla (ES2020) para el dashboard.

**Primary Dependencies**: ninguna externa. JDK: `java.base`, `java.net`, `jdk.httpserver`
(`com.sun.net.httpserver`), `java.util.concurrent`.

**Storage**: N/A — estado en memoria; entrada desde archivo `config.json`.

**Testing**: micro-framework propio (`@Prueba`, `Afirmar`, `EjecutorPruebas`) ejecutado por
`scripts/probar.ps1` (ver research.md §2).

**Target Platform**: Windows 10/11 con JDK 21; navegador moderno (Edge/Chrome/Firefox) en localhost.

**Project Type**: aplicación de escritorio local = motor + servicio web local + CLI (proyecto único).

**Performance Goals**: reflejar cada tick en el dashboard en < 1 s a 50 ms/tick con 50 procesos
(SC-004); respuesta de CLI < 2 s (SC-005).

**Constraints**: cero dependencias de terceros en ejecución; solo loopback; determinismo total
(FR-032); 100 % español en código, comentarios y mensajes.

**Scale/Scope**: escenarios didácticos de hasta ~50 procesos y unos miles de ticks; un controlador
y varios observadores.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Verificación | Estado |
|---|---|---|
| I. Stack estándar, cero dependencias | Java 21 + JDK únicamente; `HttpServer` nativo; frontend Vanilla sin CDN; JSON propio; pruebas sin JUnit (ni siquiera se usa la excepción permitida) | ✅ |
| II. Arquitectura en capas | `motor` → `recursos` → `modelo`; `entrada` → `modelo`, `json`; `interfaz.web` → `motor`, `modelo`, `json`. Motor y recursos sin E/S. Tick determinista sin hilos; hilo de reloj y lecturas sincronizados en `ControladorSimulacion` | ✅ |
| III. Gestión de recursos | `Planificador` (Estrategia) con RR; ciclo de vida con transiciones validadas y registradas; E/S → Bloqueado conservando memoria; `PoliticaAsignacion` con Best-Fit y First-Fit seleccionables; fusión de huecos; admisión solo si hay memoria; rechazo si excede RAM total | ✅ |
| IV. Interfaces duales | Dashboard con colas, mapa de RAM, Gantt, métricas, controles; `gestor.ps1` contra API local con códigos de salida ≠ 0 en errores | ✅ |
| V. Entrada JSON | `config.json` con validación completa previa al arranque; quantum, RAM, `politicaMemoria` y `planificador` configurables en el JSON (quantum, RAM y política también desde la interfaz) | ✅ |
| VI. Calidad y trazabilidad | Pruebas primero para quantum, PCB, E/S, métricas, memoria; escenarios de referencia de la spec; bitácora de eventos; todo en español | ✅ |
| Restricciones adicionales | Solo JDK en Windows; loopback; fórmulas documentadas; JSON de salida propio | ✅ |

**Re-evaluación post-diseño (Fase 1)**: data-model, contratos y quickstart mantienen las
dependencias entre capas y no introducen bibliotecas. Sin nuevas violaciones. ✅

**Re-evaluación tras `/speckit-analyze` (2026-10-15)**: se resolvieron C1 (First-Fit seleccionable,
Principio III) y C2 (política y planificador configurables, Principio V). Ver research.md §9.
Sin violaciones pendientes. ✅

## Project Structure

### Documentation (this feature)

```text
specs/001-simulador-procesos-memoria/
├── plan.md              # Este archivo
├── research.md          # Fase 0
├── data-model.md        # Fase 1
├── quickstart.md        # Fase 1
├── contracts/
│   ├── config-json.md   # Formato y validación del escenario
│   ├── api-http.md      # API HTTP local
│   └── cli-gestor.md    # Contrato de gestor.ps1
└── tasks.md             # Fase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/gestor/
├── Principal.java                    # main: argumentos, carga, arranque del servidor
├── modelo/                           # EstadoProceso, EventoES, DefinicionProceso,
│                                     # PoliticaMemoria, AlgoritmoPlanificacion,
│                                     # ConfiguracionSimulacion, Escenario, Proceso
├── json/                             # LectorJson, EscritorJson, ErrorJson
├── entrada/                          # LectorEscenario (valida), ErrorConfiguracion
├── recursos/
│   ├── cpu/                          # Planificador, PlanificadorRoundRobin
│   └── memoria/                      # Bloque, MapaMemoria, PoliticaAsignacion, MejorAjuste,
│                                     # PrimerAjuste
├── motor/                            # Motor, FabricaRecursos, ControladorSimulacion, ModoSimulacion,
│                                     # Bitacora, EventoBitacora, TramoGantt, Metricas,
│                                     # Instantanea (+ records anidados)
└── interfaz/web/                     # ServidorWeb, ManejadorApi, ManejadorEstaticos,
                                      # ConvertidorInstantanea (Instantanea → JSON)
src/main/resources/web/               # index.html, estilos.css, app.js

src/test/java/gestor/
├── pruebas/                          # Prueba (anotación), Afirmar, EjecutorPruebas
├── modelo/                           # TransicionesPcbPrueba
├── json/                             # LectorJsonPrueba, EscritorJsonPrueba
├── entrada/                          # LectorEscenarioPrueba
├── recursos/cpu/                     # PlanificadorRoundRobinPrueba
├── recursos/memoria/                 # MapaMemoriaPrueba, MejorAjustePrueba, PrimerAjustePrueba
├── motor/                            # MotorRoundRobinPrueba, MotorEntradaSalidaPrueba,
│                                     # MotorAdmisionMemoriaPrueba, MetricasPrueba,
│                                     # FabricaRecursosPrueba, MotorInstantaneaPrueba,
│                                     # ControladorSimulacionPrueba, RendimientoPrueba
└── interfaz/web/                     # ApiHttpPrueba
src/test/recursos/invalidos/          # archivos JSON inválidos para la batería de SC-002
src/test/recursos/validos/            # completo.json, minimo.json

scripts/                              # compilar.ps1, probar.ps1, iniciar.ps1, probar-cli.ps1,
                                      # verificar-capas.ps1
ejemplos/                             # referencia-rr.json, referencia-es.json, best-fit.json,
                                      # first-fit.json, inanicion.json, carga-50.json, config.json
gestor.ps1                            # CLI (UTF-8 con BOM)
config.json                           # escenario por defecto (copia de ejemplos/config.json)
build/                                # salida de compilación (ignorada)
```

**Structure Decision**: proyecto único Java con capas como paquetes (`modelo`, `json`, `entrada`,
`recursos`, `motor`, `interfaz.web`); los estáticos web viajan dentro del jar como recursos.
Dependencias permitidas entre paquetes: `interfaz.web → motor, modelo, json`;
`motor → recursos, modelo`; `recursos → modelo`; `entrada → modelo, json`. `Principal` las
compone y es el único que usa `entrada` para cargar y releer el escenario. La dirección de las
dependencias se comprueba con `scripts/verificar-capas.ps1` y en revisión de código
(constitución, Flujo de Desarrollo).

## Complexity Tracking

Sin violaciones que justificar.
