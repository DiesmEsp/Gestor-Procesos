# Guía de traspaso: Gestor-Procesos

Simulador educativo de planificación de procesos (Round Robin con E/S) y memoria RAM (partición
variable con Best-Fit) para Sistemas Operativos, con dashboard web y CLI PowerShell.

## Estado actual

- **Fases completadas**: diseño, infraestructura, fundaciones, US1/US2, carga JSON de US5,
  dashboard web de US3, CLI de US4 y verificación final (**T001–T065**). Hay escritor JSON propio, instantáneas
  inmutables, controlador con reloj y API local. `config.json` raíz contiene el escenario didáctico de
  seis procesos. `.\scripts\probar.ps1` reporta **155 pruebas, 0 fallidas** con JDK 21 en
  PowerShell 5.1 y 7. `.\scripts\iniciar.ps1` valida, informa el resumen y arranca el servidor.
  `/` sirve el dashboard completo. `scripts/probar-cli.ps1` reporta **7 pruebas CLI, 0 fallidas**
  en ambas versiones de PowerShell y comprueba SC-005 con invocaciones en procesos separados.
- **Especificación concluida**: las 65 tareas están completadas. El informe de
  [validación final](specs/001-simulador-procesos-memoria/validacion-final.md) recoge los
  comandos, mediciones y comprobaciones de `quickstart.md` §1–§5.

`verificar-capas.ps1` se ejecuta al final de `probar.ps1`, rechaza importaciones prohibidas
y devuelve código 1 ante infracciones. Se probó con 20 entradas aisladas. Todos los tipos
y métodos públicos tienen Javadoc en español; `javadoc -Xdoclint:all -Werror` termina sin
advertencias. El README documenta uso, configuración, orden temporal, métricas y capas.

`RendimientoPrueba` añade tres comprobaciones: 2000 llamadas a tick, captura y serialización
con media inferior a 10 ms; respuesta incremental inferior a 500 KB; y serialización final
inferior a 200 ms. Con 50 procesos a 50 ms por tick, el navegador completó 500 ticks y
conservó los 822 eventos. Se observaron 41,5 ms de latencia máxima y 323 ms de antigüedad
máxima del estado visible. Los atributos `data-*` de `#conexion` permiten inspeccionar
cantidad de respuestas y latencias sin cambiar la presentación.

El dashboard no usa bibliotecas: serializa las peticiones GET/POST para impedir respuestas
fuera de orden, consulta cada 200 ms y acumula eventos desde `totalEventos`. Si el contador
decrece, recupera desde cero. Respeta las ediciones pendientes de controles y solo envía
los campos modificados. Se verificaron RR, E/S, anti-inanición, errores, pausa/reinicio,
desconexión y diseño adaptable a 1280 px y móvil.

`gestor.ps1` y `scripts/probar-cli.ps1` están en UTF-8 con BOM. La CLI usa `Invoke-WebRequest`
con plazo de 2 s, lee UTF-8 explícitamente y maneja salidas 0–5 según el contrato. Para evitar
retrasos de resolución IPv6 y proxies de Internet al consultar el HttpServer local del JDK,
conecta por loopback IPv4; los mensajes conservan la dirección `localhost` del contrato.
La batería reserva 18080, usa `Measure-Command` para las consultas y cierra Java en `finally`.

El controlador acepta `(Supplier<Escenario>, false)` para pruebas sin reloj y ofrece
`avanzarManual()` en modo EN_CURSO. El constructor habitual activa el daemon
`reloj-simulacion`. El reloj, las acciones y las capturas comparten un monitor. La prueba
SC-006 compara una ejecución manual con el reloj real, dos pausas y cambios de velocidad.
Al consultar una instantánea, el expropiado pendiente figura como Listo y la CPU está libre;
el registro de expropiación mantiene el orden del siguiente tick especificado por el motor.

`Principal` compone el proveedor que relee el archivo al reiniciar. Solo las modificaciones
explícitas de quantum, RAM y política se conservan sobre el archivo nuevo; si la RAM no basta
se usa la del archivo con aviso. Un error de relectura conserva el último escenario válido.
La API valida todos los campos antes de aplicar cambios y devuelve errores JSON en español.
Se verificaron puerto ocupado, loopback, consultas concurrentes, bitácora incremental y el
jar con sus recursos, sin infracciones de capas ni dependencias externas.

## Documentos clave (leer en este orden)

1. [Constitución](.specify/memory/constitution.md): reglas no negociables (Java 21, solo la
   biblioteca estándar, capas, TDD y todo en español).
2. [Especificación](specs/001-simulador-procesos-memoria/spec.md): historias, requisitos (FR) y
   criterios de éxito (SC). La sección **Glosario y Modelo de Tiempo** fija el orden exacto de
   eventos por tick.
3. [Plan](specs/001-simulador-procesos-memoria/plan.md) y
   [investigación](specs/001-simulador-procesos-memoria/research.md): decisiones técnicas.
4. [Modelo de datos](specs/001-simulador-procesos-memoria/data-model.md): entidades y el algoritmo
   de `avanzarTick()`.
5. [Contratos](specs/001-simulador-procesos-memoria/contracts/): `config.json`, API HTTP y CLI.
6. [Tareas](specs/001-simulador-procesos-memoria/tasks.md): 65 tareas ordenadas por fases e
   historias.
7. [Guía rápida](specs/001-simulador-procesos-memoria/quickstart.md): cómo validar el resultado.

## Requisitos

- Windows 10/11 con PowerShell 5.1 o 7.
- **JDK 21** en el `PATH`. No se usan Maven, Gradle ni JUnit: la compilación se hace con scripts
  PowerShell (`javac`/`jar`) y las pruebas con un micro-framework propio (ver research.md §1–2).
- Opcional: [Claude Code](https://claude.com/claude-code) con Spec Kit. Los comandos `/speckit-*`
  ya están incluidos en `.claude/skills/`.

## Cómo continuar

```powershell
git clone https://github.com/DiesmEsp/Gestor-Procesos.git
cd Gestor-Procesos
git switch 001-simulador-procesos-memoria
```

Hay que trabajar en la rama `001-simulador-procesos-memoria`. El archivo `.specify/feature.json`
está versionado a propósito (Spec Kit lo ignora por defecto): apunta a
`specs/001-simulador-procesos-memoria`, y sin él los comandos `/speckit-*` no encuentran la
funcionalidad.

- **Con Claude Code**: ejecutar `/speckit-implement`, que recorre `tasks.md` fase por fase y marca
  las tareas completadas.
- **A mano**: seguir `tasks.md` en orden y marcar `- [x]` en cada tarea terminada. Por cada tarea
  de implementación, escribir primero la prueba y verificar que falla (Principio VI).
- **MVP sugerido**: fases 1–3 (T001–T035), es decir, el motor Round Robin con E/S verificado con
  los escenarios de referencia de la spec.

## Hallazgos de `/speckit-analyze` (todos resueltos en la documentación)

| ID | Severidad | Resolución |
|---|---|---|
| C1 | Crítico | First-Fit y Best-Fit seleccionables (FR-014, FR-014a; tareas T011, T024, T028) |
| C2 | Crítico | `configuracion.politicaMemoria` y `configuracion.planificador` en contrato, validación, API y dashboard (research.md §9) |
| I1 | Medio | El Glosario (pasos 1–2) aplica la regla del proceso bloqueante (FR-015a) |
| I2 | Medio | US3 escenario 2 y FR-023: la vista muestra el estado más reciente con < 1 s de retraso; la bitácora no omite eventos |
| I3 | Medio | La CLI muestra "Quantums (ticks) consumidos/restantes" |
| A1 | Medio | Reiniciar conserva solo los valores modificados desde la interfaz; el resto viene del archivo releído |
| A2 | Medio | T059 con casos deterministas y en orden fijo |
| U1 | Medio | T064 mide el tamaño de la instantánea; T065 incluye una verificación manual cronometrada de SC-004 |
| A3 | Bajo | US3 escenario 1: en el instante 0 sin iniciar, las colas están vacías |
| U2 | Bajo | `gestor.ps1` con timeout de 2 s; T059 mide < 2 s por consulta (SC-005) |
| U3 | Bajo | `Instantanea.con(modo, velocidadMs, aviso)` compone los datos del motor y del controlador (T050, T051) |
| F1 | Bajo | Árbol de pruebas, scripts y ejemplos de `plan.md` sincronizado con `tasks.md` |
| F2 | Bajo | Dependencias entre capas corregidas en `plan.md` (`interfaz.web → motor, modelo, json`) |

Una segunda pasada de `/speckit-analyze` (0 críticos) encontró 9 hallazgos más, también resueltos:

| ID | Severidad | Resolución |
|---|---|---|
| I1 | Medio | `/api/proceso/{id}` incluye `bloqueante` y `ticksRestantesES`, que la CLI necesita |
| A1 | Medio | Bitácora incremental (`GET /api/estado?desdeEvento=n` + `totalEventos`); el navegador la acumula (research.md §5) |
| U1 | Medio | Las pruebas muestran `[OK]`/`[FALLO]` (ASCII) y los scripts fuerzan UTF-8 en la consola |
| U2 | Medio | `ErrorConfiguracion extends RuntimeException`; `reiniciar()` la captura y la convierte en `aviso` |
| G1, G2 | Bajo | El dashboard muestra adelantamientos, el proceso bloqueante y la causa de la espera de memoria |
| U3 | Bajo | Auxiliar compartido de pruebas `gestor.pruebas.InvariantesMemoria` |
| F1 | Bajo | `verificar-capas.ps1` comprueba que solo `Principal` use `entrada` |
| F2 | Bajo | Objetivo de la Fase 6 y lista de motivos de la bitácora completados |

## Decisiones ya tomadas (no reabrir sin enmendar los documentos)

- Unidad de tiempo: el **tick**. El quantum es el número de ticks por turno (defecto 1) y
  `enQuantum` cuenta los ticks de CPU que el proceso ya consumió.
- Espera de memoria: se admite a todo proceso que quepa, con un límite de **5 adelantamientos**
  (configurable con `maxAdelantamientos`).
- Tiempo de espera = ticks en Listo + ticks en Espera de Memoria.
- Orden en Listos dentro del mismo instante: recién llegado → retornado de E/S → expropiado.
- Dashboard con botón **Reiniciar**. La RAM y la política de memoria solo pueden cambiarse en el
  instante 0. El planificador solo se elige en `config.json` (por ahora solo existe Round Robin).
- `gestor.ps1` debe guardarse en **UTF-8 con BOM** para que PowerShell 5.1 muestre bien las tildes.
