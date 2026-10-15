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
6. [Tareas](specs/001-simulador-procesos-memoria/tasks.md): 60 tareas ordenadas por fases e
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
- **MVP sugerido**: fases 1–3 (T001–T030), es decir, el motor Round Robin con E/S verificado con
  los escenarios de referencia de la spec.

## Pendientes detectados por `/speckit-analyze` (resolver antes o durante la implementación)

| ID | Severidad | Descripción | Acción sugerida |
|---|---|---|---|
| C1 | CRÍTICO | La constitución (Principio III) exige poder elegir entre First-Fit y Best-Fit; la spec solo pide Best-Fit | Añadir `PrimerAjuste` (implementa `PoliticaAsignacion`) con sus pruebas, o aprobar la desviación por escrito en `plan.md` |
| C2 | CRÍTICO | El Principio V exige configurar política de memoria y planificador desde el JSON o desde argumentos | Añadir `configuracion.politicaMemoria` (`BEST_FIT` por defecto / `FIRST_FIT`) y `configuracion.planificador` (`ROUND_ROBIN`) al contrato, a T034/T037 y al dashboard |
| I1 | Medio | El Glosario (pasos 1–2) no menciona la regla de proceso bloqueante (FR-015a) | Añadir «salvo que exista un proceso bloqueante» |
| I2 | Medio | US3 escenario 2 dice «la vista se actualiza en cada tick»; con sondeo de 200 ms y ticks de 50 ms la vista se salta estados (la bitácora no) | Reformular: «refleja el estado más reciente en < 1 s» |
| I3 | Medio | La CLI dice «Quantums consumidos/restantes» pero los valores son ticks | Etiquetar como ticks |
| A1 | Medio | Al Reiniciar no está claro si gana el `config.json` releído o el quantum/RAM fijados en la interfaz | Conservar solo los valores modificados explícitamente desde la interfaz |
| A2 | Medio | T054 tiene casos de prueba no deterministas | P2 «pendiente» con código 0; definir cómo se llega al estado pausado |
| U1 | Medio | SC-004 (dashboard < 1 s con 50 procesos) solo se mide en el motor | Añadir una medición o verificación manual en T060 |

## Decisiones ya tomadas (no reabrir sin enmendar los documentos)

- Unidad de tiempo: el **tick**. El quantum es el número de ticks por turno (defecto 1) y
  `enQuantum` cuenta los ticks de CPU que el proceso ya consumió.
- Espera de memoria: se admite a todo proceso que quepa, con un límite de **5 adelantamientos**
  (configurable con `maxAdelantamientos`).
- Tiempo de espera = ticks en Listo + ticks en Espera de Memoria.
- Orden en Listos dentro del mismo instante: recién llegado → retornado de E/S → expropiado.
- Dashboard con botón **Reiniciar**. La RAM solo puede cambiarse en el instante 0.
- `gestor.ps1` debe guardarse en **UTF-8 con BOM** para que PowerShell 5.1 muestre bien las tildes.
