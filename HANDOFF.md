# Guía de traspaso: Gestor-Procesos

Simulador educativo de planificación de procesos (Round Robin con E/S) y memoria RAM (partición
variable con Best-Fit) para Sistemas Operativos, con dashboard web y CLI PowerShell.

## Estado actual

- **Fase completada**: diseño con Spec Kit (constitución → especificación → clarificación → plan →
  tareas → análisis). **Todavía no hay código.**
- **Siguiente paso**: tarea **T001** de
  [`specs/001-simulador-procesos-memoria/tasks.md`](specs/001-simulador-procesos-memoria/tasks.md).

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

Antes de implementar conviene volver a ejecutar `/speckit-analyze` para confirmar que no quedan
inconsistencias.

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
