# Modelo de Datos (Fase 1): Simulador de Gestión de Procesos y Memoria RAM

Paquetes según la capa (ver `plan.md`). Todos los nombres en español (Principio VI).

## Capa `gestor.modelo`

### `EstadoProceso` (enum)

`PENDIENTE_LLEGADA`, `NUEVO` (Nuevo/Espera de Memoria), `LISTO`, `EJECUCION`, `BLOQUEADO`,
`TERMINADO`.

Transiciones válidas (cualquier otra lanza `IllegalStateException` — FR-007):

```text
PENDIENTE_LLEGADA ──llegada──▶ NUEVO ──memoria asignada──▶ LISTO ──despacho──▶ EJECUCION
                                                            ▲                    │
                                                            ├── quantum agotado ─┤
                                                            │                    ├──E/S──▶ BLOQUEADO
                                                            └──── fin de E/S ◀───┼─────────┘
                                                                                 └──fin ráfaga──▶ TERMINADO
```

Un proceso que llega y cabe pasa `PENDIENTE_LLEGADA → NUEVO → LISTO` en el mismo instante (dos
eventos en la bitácora, 0 ticks de espera de memoria).

### `EventoES` (record)

| Campo | Tipo | Regla |
|---|---|---|
| `enQuantum` | int | 1 ≤ valor ≤ `tiempoRafaga`−1; estrictamente creciente en la lista |
| `duracion` | int | ≥ 1 |

### `DefinicionProceso` (record, inmutable; lo produce la entrada)

| Campo | Tipo | Regla |
|---|---|---|
| `id` | String | no vacío, único en el escenario |
| `tiempoLlegada` | int | ≥ 0 |
| `tiempoRafaga` | int | ≥ 1 |
| `memoria` | int | ≥ 1 y ≤ `memoriaTotal` |
| `prioridad` | Integer | opcional (null); no afecta a RR |
| `bloqueosES` | List<EventoES> | por defecto vacía |
| `ordenArchivo` | int | posición en el archivo; desempata llegadas simultáneas |

### `PoliticaMemoria` (enum)

`BEST_FIT` ("Best-Fit"), `FIRST_FIT` ("First-Fit"). `static Optional<PoliticaMemoria>
desdeCodigo(String)` usa el nombre exacto de la constante (el valor del JSON).

### `AlgoritmoPlanificacion` (enum)

`ROUND_ROBIN` ("Round Robin"). Se añadirán `FCFS`, `PRIORIDADES` y `SJF` en funcionalidades
futuras. Mismo `desdeCodigo(String)`.

### `ConfiguracionSimulacion` (record)

| Campo | Tipo | Defecto | Regla |
|---|---|---|---|
| `memoriaTotal` | int | 1024 | ≥ 1 |
| `quantum` | int | 1 | ≥ 1 |
| `maxAdelantamientos` | int | 5 | ≥ 1 |
| `politicaMemoria` | PoliticaMemoria | BEST_FIT | `BEST_FIT` o `FIRST_FIT` |
| `planificador` | AlgoritmoPlanificacion | ROUND_ROBIN | `ROUND_ROBIN` |

### `Escenario` (record)

`ConfiguracionSimulacion configuracion` + `List<DefinicionProceso> procesos` (no vacía).

### `Proceso` (PCB, mutable, propiedad exclusiva del Motor)

| Campo | Tipo | Descripción |
|---|---|---|
| `definicion` | DefinicionProceso | datos de entrada |
| `estado` | EstadoProceso | estado actual |
| `ticksConsumidos` | int | ticks de CPU usados |
| `ticksRestantes` | int (derivado) | `tiempoRafaga − ticksConsumidos` |
| `indiceSiguienteES` | int | próximo evento de `bloqueosES` |
| `ticksQuantumUsados` | int | ticks en el turno actual (se reinicia al despachar) |
| `finBloqueo` | int | instante en que vuelve a Listo (solo en BLOQUEADO) |
| `baseBloque`, `tamanoBloque` | int | bloque asignado (`tieneMemoria()` falso si no tiene); se guardan como enteros para que `modelo` no dependa de `recursos` |
| `ticksEsperaListo` | int | ticks en LISTO |
| `ticksEsperaMemoria` | int | ticks en NUEVO |
| `ticksES` | int | ticks en BLOQUEADO |
| `adelantamientos` | int | contador FR-015a (solo relevante en NUEVO) |
| `instanteFinalizacion` | Integer | null hasta TERMINADO |

Derivados: `tiempoEspera = ticksEsperaListo + ticksEsperaMemoria` (FR-028);
`tiempoRetorno = instanteFinalizacion − tiempoLlegada` (FR-027). Invariante verificable en pruebas:
`tiempoRetorno = tiempoRafaga + ticksES + tiempoEspera`.

## Capa `gestor.recursos.memoria`

### `Bloque` (record)

`int base`, `int tamano`, `String idProceso` (null = hueco). Derivado `limite = base + tamano − 1`.

### `MapaMemoria`

Lista ordenada por `base` de bloques ocupados y huecos que cubre `[0, memoriaTotal−1]`.

Invariantes (SC-007, comprobados tras cada operación en pruebas):
1. Los bloques son contiguos y sin solapamiento; la suma de tamaños = `memoriaTotal`.
2. Nunca hay dos huecos adyacentes.

Operaciones:
- `Optional<Bloque> asignar(String id, int tamano)` — delega la elección del hueco en
  `PoliticaAsignacion`; divide el hueco (proceso al inicio, sobrante como hueco).
- `void liberar(String id)` — convierte el bloque en hueco y fusiona con vecinos (FR-016).
- `int memoriaLibre()`, `int mayorHueco()`, `int fragmentacionExterna()` (= libre − mayor hueco).
- `List<Bloque> bloques()` — copia inmutable para instantáneas.

### `PoliticaAsignacion` (interfaz) → `MejorAjuste` (Best-Fit), `PrimerAjuste` (First-Fit)

`Optional<Bloque> elegirHueco(List<Bloque> huecos, int tamano)`, con `huecos` ordenados por base:
- `MejorAjuste`: menor tamaño ≥ requerido; empate → menor base (FR-014).
- `PrimerAjuste`: el primero (menor base) con tamaño ≥ requerido (FR-014).

## Capa `gestor.recursos.cpu`

### `Planificador` (interfaz) → `PlanificadorRoundRobin`

- `void encolar(Proceso p)` — al final de la cola de Listos.
- `Proceso siguiente()` — extrae el primero (o null).
- `boolean debeExpropiar(Proceso enEjecucion, int quantum)` — RR: `ticksQuantumUsados ≥ quantum`.
- `List<Proceso> cola()` — vista de solo lectura en orden.

Preparado para `PlanificadorFCFS`, `PlanificadorPrioridades`, `PlanificadorSJF` (fuera de alcance).

## Capa `gestor.motor`

### `FabricaRecursos`

Traduce la configuración a implementaciones de la capa de recursos, para que ni `modelo` ni
`Motor` conozcan las clases concretas:
- `static PoliticaAsignacion crearPolitica(PoliticaMemoria)`: `BEST_FIT → MejorAjuste`,
  `FIRST_FIT → PrimerAjuste`.
- `static Planificador crearPlanificador(AlgoritmoPlanificacion)`: `ROUND_ROBIN →
  PlanificadorRoundRobin`.

### `Motor`

Estado: `instanteActual`, `Escenario`, quantum vigente, procesos, `MapaMemoria`, `Planificador`,
cola `esperaMemoria`, `bloqueados`, `enEjecucion`, `expropiadoPendiente`, `Bitacora`, `Gantt`,
`ticksCpuOcupada`.

`void avanzarTick()` — algoritmo (orden de la spec):

1. **Admisión** (pasos 1 y 2): candidatos = `esperaMemoria` + llegadas con `tiempoLlegada == t`
   (marcadas `NUEVO`). Recorrido con bandera `bloqueada` según `research.md` §7. Admitidos →
   `LISTO` y `planificador.encolar`.
2. **Fin de E/S**: bloqueados con `finBloqueo == t` (orden de bloqueo) → `LISTO` y encolar.
3. **Expropiado**: si `expropiadoPendiente != null` → `LISTO` y encolar.
4. **Despacho**: si `enEjecucion == null`, `siguiente()` → `EJECUCION`, `ticksQuantumUsados = 0`
   (usa el quantum vigente en este momento).
5. **Ejecución del tick**: si hay proceso, `ticksConsumidos++`, `ticksQuantumUsados++`,
   `ticksCpuOcupada++` y se extiende/abre el tramo de Gantt; si no, tramo ocioso. Se suman
   `ticksEsperaListo` / `ticksEsperaMemoria` / `ticksES` a los procesos en esas colas.
6. **Evaluación a t+1** (precedencia): ráfaga completa → `TERMINADO`, `liberar` memoria,
   `instanteFinalizacion = t+1`; si `ticksConsumidos == bloqueosES[i].enQuantum` → `BLOQUEADO`,
   `finBloqueo = t+1+duracion`; si `debeExpropiar` → `expropiadoPendiente`.
7. `instanteActual = t+1`. Si todos están `TERMINADO`, la simulación queda finalizada.

Otras operaciones: `cambiarQuantum(int)` (aplica en el próximo despacho),
`Instantanea instantanea()`, `boolean finalizada()`.

### `Bitacora` / `EventoBitacora` (record)

`int instante`, `String idProceso`, `EstadoProceso origen`, `EstadoProceso destino`,
`String motivo` (p. ej. "Llegada", "Memoria asignada [70–94]", "Quantum agotado",
"Solicita E/S (3 ticks)", "Fin de E/S", "Ráfaga completada", "Sin memoria contigua suficiente").

### `TramoGantt` (record)

`String idProceso` (null = ocioso), `int inicio`, `int fin` (exclusivo). Un tramo por turno; los
ticks ociosos consecutivos se agrupan.

### `ControladorSimulacion`

Modo: `DETENIDA` (instante 0 sin iniciar), `EN_CURSO`, `PAUSADA`, `FINALIZADA`. Posee el hilo de
reloj, `velocidadMs` (50–2000, defecto 500) y un `Supplier<Escenario>` para reiniciar.

| Acción | Precondición | Efecto |
|---|---|---|
| `iniciar()` | DETENIDA o PAUSADA | → EN_CURSO |
| `pausar()` | EN_CURSO | → PAUSADA |
| `reiniciar()` | cualquiera | relee escenario (si falla, usa el último válido y devuelve aviso); superpone al escenario releído solo los valores de quantum, RAM y política modificados desde la interfaz (si la RAM conservada no aloja algún proceso, usa la del archivo y deja aviso); → DETENIDA |
| `cambiarQuantum(q)` | q ≥ 1 | aplica en el siguiente despacho |
| `cambiarMemoriaTotal(m)` | DETENIDA y m ≥ memoria de todo proceso | recrea el mapa |
| `cambiarPoliticaMemoria(p)` | DETENIDA | recrea el motor con la nueva política (FR-014a) |
| `cambiarVelocidad(ms)` | 50 ≤ ms ≤ 2000 | cambia el intervalo |

### `Instantanea` (records inmutables)

Ver la forma JSON exacta en `contracts/api-http.md` (`GET /api/estado`). `Motor.instantanea()`
produce la parte del motor con `modo`, `velocidadMs` y `aviso` neutros; `ControladorSimulacion`
la completa con `Instantanea.con(modo, velocidadMs, aviso)`. Incluye: instante, modo,
configuración vigente, colas, proceso en CPU, procesos (resumen), mapa de memoria con métricas de
fragmentación, Gantt, bitácora y métricas.

### Métricas

- `esperaPromedio`, `retornoPromedio`: media sobre procesos TERMINADO (null si ninguno).
- `utilizacionCpu = ticksCpuOcupada / instanteActual × 100` (null en instante 0); al finalizar,
  `instanteActual` = finalización del último proceso (FR-029).
