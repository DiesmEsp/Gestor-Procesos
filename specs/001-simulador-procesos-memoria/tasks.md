---

description: "Lista de tareas para implementar el Simulador de Gestión de Procesos y Memoria RAM"
---

# Tasks: Simulador de Gestión de Procesos y Memoria RAM

**Input**: Design documents from `/specs/001-simulador-procesos-memoria/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: OBLIGATORIAS. La constitución (Principio VI) exige desarrollo guiado por pruebas: cada
tarea de prueba se escribe ANTES de su implementación y debe fallar primero. Se ejecutan con
`.\scripts\probar.ps1` (micro-framework propio, research.md §2).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

**Convenciones**: todo el código, nombres, comentarios y mensajes en español. Solo biblioteca
estándar de Java 21. Paquete raíz `gestor`. Compilación con `javac --release 21 -encoding UTF-8`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1 = RR + E/S, US2 = memoria Best-Fit,
  US3 = dashboard web, US4 = CLI PowerShell, US5 = carga de `config.json`)
- Include exact file paths in descriptions

## Path Conventions

- Producción: `src/main/java/gestor/...`, estáticos en `src/main/resources/web/`
- Pruebas: `src/test/java/gestor/...`, datos de prueba en `src/test/recursos/`
- Scripts: `scripts/`; CLI: `gestor.ps1` en la raíz; escenarios: `ejemplos/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: estructura del proyecto, scripts de construcción y micro-framework de pruebas

- [x] T001 Crear la estructura de directorios del plan (`src/main/java/gestor/{modelo,json,entrada,recursos/cpu,recursos/memoria,motor,interfaz/web}`, `src/main/resources/web`, `src/test/java/gestor/{pruebas,json,entrada,recursos/cpu,recursos/memoria,motor,interfaz/web}`, `src/test/recursos/invalidos`, `scripts`, `ejemplos`) y un `.gitignore` en la raíz que excluya `build/`
- [x] T002 [P] Crear `scripts/compilar.ps1`: borra y recrea `build/clases`, compila todos los `.java` de `src/main/java` con `javac --release 21 -encoding UTF-8 -d build/clases`, copia `src/main/resources/*` a `build/clases`, genera `build/gestor-procesos.jar` con `jar --create --file build/gestor-procesos.jar --main-class gestor.Principal -C build/clases .`; termina con `exit 1` y mensaje en español si `javac` o `jar` fallan
- [x] T003 [P] Crear `scripts/probar.ps1`: invoca `compilar.ps1`, compila `src/test/java` contra `build/clases` en `build/pruebas`, copia `src/test/recursos` a `build/pruebas/recursos`, obtiene los nombres de clase de todos los archivos `*Prueba.java` (ruta → `gestor.x.YPrueba`), fija `[Console]::OutputEncoding = [Text.Encoding]::UTF8` y ejecuta `java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "build/clases;build/pruebas" gestor.pruebas.EjecutorPruebas <clases...>` (para que las tildes se vean bien) con el directorio de trabajo en la raíz y propaga `$LASTEXITCODE`
- [x] T004 [P] Crear `scripts/iniciar.ps1` con parámetros `-Config` (defecto `.\config.json`) y `-Puerto` (defecto 8080): compila si no existe `build/gestor-procesos.jar` fija `[Console]::OutputEncoding = [Text.Encoding]::UTF8` y ejecuta `java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar build/gestor-procesos.jar --config <ruta> --puerto <n>`
- [x] T005 [P] Crear la anotación `@Prueba` (`@Retention(RUNTIME)`, `@Target(METHOD)`) en `src/test/java/gestor/pruebas/Prueba.java`
- [x] T006 [P] Crear `src/test/java/gestor/pruebas/Afirmar.java` con métodos estáticos `iguales(Object esperado, Object real, String mensaje)` (usa `Objects.equals`; mensaje "Se esperaba <x> pero fue <y>"), `iguales(double, double, double tolerancia, String)`, `verdadero`, `falso`, `nulo`, `noNulo`, `contiene(String texto, String fragmento, String)` y `<T extends Throwable> T lanza(Class<T>, Runnable)` que devuelve la excepción capturada; todos lanzan `AssertionError` con mensaje en español
- [x] T007 Crear `src/test/java/gestor/pruebas/EjecutorPruebas.java` (main): recibe nombres de clase, por cada método `@Prueba` crea una instancia nueva y lo invoca; imprime `[OK] Clase.metodo` o `[FALLO] Clase.metodo: <mensaje>` (marcadores ASCII: la consola de Windows usa cp850 y no muestra `✔`/`✘`) (desempaquetando `InvocationTargetException`), resumen `N pruebas, M fallidas` y `System.exit(1)` si M > 0 (depende de T005, T006)

**Checkpoint**: `.\scripts\probar.ps1` se ejecuta y reporta "0 pruebas, 0 fallidas".

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: modelo de dominio, recurso de memoria (mapa + Best-Fit + First-Fit), recurso de CPU (Round Robin) y fábrica de recursos, que necesitan todas las historias

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Pruebas fundacionales (escribir primero, deben fallar)

- [x] T008 [P] Escribir `src/test/java/gestor/modelo/TransicionesPcbPrueba.java`: cada transición válida de data-model.md (`PENDIENTE_LLEGADA→NUEVO`, `NUEVO→LISTO`, `LISTO→EJECUCION`, `EJECUCION→LISTO`, `EJECUCION→BLOQUEADO`, `BLOQUEADO→LISTO`, `EJECUCION→TERMINADO`) se acepta; todas las demás combinaciones (p. ej. `LISTO→BLOQUEADO`, `TERMINADO→LISTO`, `NUEVO→EJECUCION`) lanzan `IllegalStateException` con mensaje en español; `ticksRestantes = tiempoRafaga − ticksConsumidos`; `tiempoEspera = ticksEsperaListo + ticksEsperaMemoria`; `tiempoRetorno = instanteFinalizacion − tiempoLlegada`
- [x] T009 [P] Escribir `src/test/java/gestor/recursos/memoria/MapaMemoriaPrueba.java`: mapa nuevo de 100 MB = un hueco [0–99]; asignar divide el hueco (proceso al inicio, sobrante como hueco); escenario 3 de la Historia 2 (A [0–49], B [50–69], C [70–89], hueco [90–99]; liberar A y C → huecos [0–49] y [70–99]; liberar B → único hueco [0–99]); `memoriaLibre`, `mayorHueco`, `fragmentacionExterna` (= libre − mayor hueco); crear además el auxiliar compartido `src/test/java/gestor/pruebas/InvariantesMemoria.java` con `public static void verificar(MapaMemoria mapa)`, que comprueba contigüidad, suma de tamaños = total y ausencia de huecos adyacentes (SC-007), y llamarlo tras cada operación; asignar con memoria insuficiente devuelve `Optional.empty()` sin alterar el mapa; liberar un id inexistente lanza `IllegalArgumentException`
- [x] T010 [P] Escribir `src/test/java/gestor/recursos/memoria/MejorAjustePrueba.java`: escenario 1 de la Historia 2 (huecos [0–49] y [70–99], proceso de 25 MB → hueco con base 70); empate de tamaño → menor base (escenario 2); ningún hueco suficiente → vacío; hueco de tamaño exacto se elige sobre uno mayor
- [x] T011 [P] Escribir `src/test/java/gestor/recursos/memoria/PrimerAjustePrueba.java`: escenario 8 de la Historia 2 (huecos [0–49] y [70–99], proceso de 25 MB → hueco con base 0); elige el primer hueco que cabe aunque más adelante haya uno de tamaño exacto; ningún hueco suficiente → vacío; `nombre()` = "First-Fit"
- [x] T012 [P] Escribir `src/test/java/gestor/recursos/cpu/PlanificadorRoundRobinPrueba.java`: FIFO al encolar/extraer; `siguiente()` con cola vacía devuelve `null`; `debeExpropiar` es verdadero solo si `ticksQuantumUsados ≥ quantum`; `cola()` devuelve vista no modificable en orden
- [x] T013 [P] Escribir `src/test/java/gestor/motor/FabricaRecursosPrueba.java`: `crearPolitica(BEST_FIT)` devuelve `MejorAjuste`, `crearPolitica(FIRST_FIT)` devuelve `PrimerAjuste`, `crearPlanificador(ROUND_ROBIN)` devuelve `PlanificadorRoundRobin`; `PoliticaMemoria.desdeCodigo("FIRST_FIT")` y `AlgoritmoPlanificacion.desdeCodigo("ROUND_ROBIN")` devuelven la constante; códigos desconocidos o en minúsculas devuelven `Optional.empty()`

**Lote 2 — fase roja verificada**: T008–T013 escritas, con 47 métodos `@Prueba` y el auxiliar
`InvariantesMemoria`. Antes de implementar las clases productivas, `.\scripts\probar.ps1`
terminaba con código 1 por clases ausentes. El análisis sintáctico con Java 21 no detectó errores.

**Fundaciones — fase verde verificada**: T014–T030 implementadas. `.\scripts\probar.ps1`
reporta `47 pruebas, 0 fallidas` y código de salida 0, sin modificar las pruebas del Lote 2.
También se verificaron la configuración, las copias inmutables, la gestión de bloques y eventos
de E/S del PCB, la bitácora y la agrupación de turnos y ticks ociosos del Gantt.

**Preparación del PCB en las pruebas**: se usa `Proceso(DefinicionProceso)`, la consulta
`estado()` y los mutadores en español `fijarTicksConsumidos`, `fijarTicksEsperaListo`,
`fijarTicksEsperaMemoria`, `fijarInstanteFinalizacion` y `fijarTicksQuantumUsados` para
preparar los campos de T020 sin reflexión ni acceso directo a campos privados.

### Modelo (`gestor.modelo`)

- [x] T014 [P] Crear `src/main/java/gestor/modelo/EstadoProceso.java`: enum `PENDIENTE_LLEGADA, NUEVO, LISTO, EJECUCION, BLOQUEADO, TERMINADO` con `boolean puedeTransicionarA(EstadoProceso destino)` según el diagrama de data-model.md y `String etiqueta()` ("Pendiente de llegada", "Nuevo / Espera de memoria", "Listo", "En Ejecución", "Bloqueado", "Terminado")
- [x] T015 [P] Crear `src/main/java/gestor/modelo/EventoES.java`: record `(int enQuantum, int duracion)` — reglas (validadas en la entrada): "1 ≤ valor ≤ `tiempoRafaga`−1; estrictamente creciente en la lista" y "duracion ≥ 1"
- [x] T016 [P] Crear `src/main/java/gestor/modelo/DefinicionProceso.java`: record `(String id, int tiempoLlegada, int tiempoRafaga, int memoria, Integer prioridad, List<EventoES> bloqueosES, int ordenArchivo)`; constructor compacto que copia `bloqueosES` con `List.copyOf` (null → lista vacía)
- [x] T017 [P] Crear `src/main/java/gestor/modelo/PoliticaMemoria.java` (enum `BEST_FIT` "Best-Fit", `FIRST_FIT` "First-Fit") y `src/main/java/gestor/modelo/AlgoritmoPlanificacion.java` (enum `ROUND_ROBIN` "Round Robin"), ambos con `String etiqueta()`, `static Optional<…> desdeCodigo(String codigo)` (coincidencia exacta con el nombre de la constante) y `static String valoresValidos()` ("BEST_FIT, FIRST_FIT" / "ROUND_ROBIN") para los mensajes de error
- [x] T018 Crear `src/main/java/gestor/modelo/ConfiguracionSimulacion.java`: record `(int memoriaTotal, int quantum, int maxAdelantamientos, PoliticaMemoria politicaMemoria, AlgoritmoPlanificacion planificador)` con constantes `MEMORIA_POR_DEFECTO = 1024`, `QUANTUM_POR_DEFECTO = 1`, `MAX_ADELANTAMIENTOS_POR_DEFECTO = 5`, `POLITICA_POR_DEFECTO = BEST_FIT`, `PLANIFICADOR_POR_DEFECTO = ROUND_ROBIN`, fábrica `porDefecto()` y métodos `conQuantum(int)`, `conMemoriaTotal(int)`, `conPoliticaMemoria(PoliticaMemoria)` (depende de T017)
- [x] T019 Crear `src/main/java/gestor/modelo/Escenario.java`: record `(ConfiguracionSimulacion configuracion, List<DefinicionProceso> procesos)` con copia inmutable de la lista (depende de T016, T018)
- [x] T020 Crear `src/main/java/gestor/modelo/Proceso.java` (PCB mutable) con los campos de data-model.md (`definicion`, `estado`, `ticksConsumidos`, `indiceSiguienteES`, `ticksQuantumUsados`, `finBloqueo`, el bloque asignado representado como `int baseBloque` e `int tamanoBloque` con `asignarBloque(int base, int tamano)`, `quitarBloque()` y `boolean tieneMemoria()` (así `modelo` no depende de `recursos`), `ticksEsperaListo`, `ticksEsperaMemoria`, `ticksES`, `adelantamientos`, `Integer instanteFinalizacion`), `void cambiarEstado(EstadoProceso destino)` que lanza `IllegalStateException("Transición inválida de <origen> a <destino> para el proceso <id>")`, y derivados `ticksRestantes()`, `tiempoEspera()`, `tiempoRetorno()`, `siguienteEventoES()` (Optional) — hace pasar T008 (depende de T014–T016)

### Recurso de memoria (`gestor.recursos.memoria`)

- [x] T021 [P] Crear `src/main/java/gestor/recursos/memoria/Bloque.java`: record `(int base, int tamano, String idProceso)` con `int limite()` = `base + tamano − 1` y `boolean esHueco()` (`idProceso == null`)
- [x] T022 [P] Crear `src/main/java/gestor/recursos/memoria/PoliticaAsignacion.java`: interfaz `Optional<Bloque> elegirHueco(List<Bloque> huecos, int tamano)` y `String nombre()`
- [x] T023 Crear `src/main/java/gestor/recursos/memoria/MejorAjuste.java` (Best-Fit): "menor tamaño ≥ requerido; empate → menor base" (FR-014); `nombre()` = "Best-Fit" — hace pasar T010 (depende de T021, T022)
- [x] T024 [P] Crear `src/main/java/gestor/recursos/memoria/PrimerAjuste.java` (First-Fit): "el primero (menor base) con tamaño ≥ requerido" (FR-014); `nombre()` = "First-Fit" — hace pasar T011 (depende de T021, T022)
- [x] T025 Crear `src/main/java/gestor/recursos/memoria/MapaMemoria.java`: constructor `(int memoriaTotal, PoliticaAsignacion politica)`; lista ordenada por base; `Optional<Bloque> asignar(String id, int tamano)` (proceso al inicio del hueco elegido, sobrante como hueco); `void liberar(String id)` con fusión con hueco anterior y posterior (FR-016); `memoriaLibre()`, `mayorHueco()`, `fragmentacionExterna()`, `boolean cabe(int tamano)`, `Optional<Bloque> bloqueDe(String id)`, `List<Bloque> bloques()` (copia inmutable), `int total()` — hace pasar T009 (depende de T021–T023)

### Recurso de CPU (`gestor.recursos.cpu`)

- [x] T026 [P] Crear `src/main/java/gestor/recursos/cpu/Planificador.java`: interfaz `void encolar(Proceso p)`, `Proceso siguiente()`, `boolean debeExpropiar(Proceso enEjecucion, int quantum)`, `List<Proceso> cola()`, `void vaciar()`, `String nombre()`
- [x] T027 Crear `src/main/java/gestor/recursos/cpu/PlanificadorRoundRobin.java` con `ArrayDeque<Proceso>`; `nombre()` = "Round Robin" — hace pasar T012 (depende de T020, T026)

### Tipos base del motor (`gestor.motor`)

- [x] T028 Crear `src/main/java/gestor/motor/FabricaRecursos.java` con `static PoliticaAsignacion crearPolitica(PoliticaMemoria)` (`BEST_FIT → MejorAjuste`, `FIRST_FIT → PrimerAjuste`) y `static Planificador crearPlanificador(AlgoritmoPlanificacion)` (`ROUND_ROBIN → PlanificadorRoundRobin`), usando `switch` exhaustivo sobre el enum — hace pasar T013 (depende de T017, T023, T024, T027)

- [x] T029 [P] Crear `src/main/java/gestor/motor/EventoBitacora.java` (record `int instante, String idProceso, EstadoProceso origen, EstadoProceso destino, String motivo`) y `src/main/java/gestor/motor/Bitacora.java` (`registrar(...)`, `List<EventoBitacora> eventos()` inmutable, `vaciar()`)
- [x] T030 [P] Crear `src/main/java/gestor/motor/TramoGantt.java` (record `String idProceso` — null = ocioso —, `int inicio`, `int fin` exclusivo) y `src/main/java/gestor/motor/DiagramaGantt.java` con `registrarTick(int instante, String idProceso, boolean nuevoTurno)`: abre tramo nuevo si cambia el proceso o empieza un turno, extiende el último si no; agrupa ticks ociosos consecutivos; `List<TramoGantt> tramos()` inmutable

**Checkpoint**: `.\scripts\probar.ps1` pasa todas las pruebas fundacionales (T008–T013). Fundaciones listas.

---

## Phase 3: User Story 1 - Ejecutar una simulación Round Robin con E/S (Priority: P1) 🎯 MVP

**Goal**: motor de ticks determinista que ejecuta un escenario con Round Robin expulsivo, E/S simulada y métricas, siguiendo exactamente el "Orden de eventos dentro de cada instante t" de la spec.

**Independent Test**: las pruebas construyen `Escenario` en código (sin JSON) y comparan Gantt, instantes de finalización y métricas con los valores calculados a mano de la Historia 1.

### Tests for User Story 1 ⚠️ (escribir primero, deben fallar)

- [x] T031 [P] [US1] Escribir `src/test/java/gestor/motor/MotorRoundRobinPrueba.java`: (a) escenario 1 de la Historia 1 — RAM 100, quantum 2, P1 (0, 4, 30 MB), P2 (1, 3, 20 MB) → Gantt P1[0–2) P2[2–4) P1[4–6) P2[6–7), finalizaciones 6 y 7, retornos 6 y 6, esperas 2 y 3; (b) escenario 4 — proceso que completa su ráfaga justo al agotar el quantum pasa a TERMINADO y no se reencola; (c) escenario 5 — en un mismo instante, orden de Listos: recién llegado, retornado de E/S, expropiado; (d) ningún proceso en el instante 0 → ticks ociosos agrupados en un tramo de Gantt con `idProceso` null; (e) llegadas simultáneas se encolan por `ordenArchivo`; (f) `cambiarQuantum` en curso se aplica desde el siguiente despacho; (g) la bitácora contiene `PENDIENTE_LLEGADA→NUEVO` y `NUEVO→LISTO` en el mismo instante para un proceso que cabe; (h) `finalizada()` es verdadero solo cuando todos están TERMINADO; (i) ejecutar el escenario dos veces produce bitácoras idénticas (FR-032)
- [x] T032 [P] [US1] Escribir `src/test/java/gestor/motor/MotorEntradaSalidaPrueba.java`: (a) escenario 2 de la Historia 1 — quantum 1, P1 (0, 3, 10 MB, E/S {enQuantum 1, duracion 2}) → corre tick 0, BLOQUEADO ticks 1–2 (CPU ociosa), LISTO en instante 3, termina en 5; retorno 5, espera 0, `ticksES` 2; (b) escenario 3 — quantum 3 y `enQuantum` 1 → abandona la CPU tras 1 tick sin agotar el quantum; (c) E/S y fin de quantum en el mismo tick → va a BLOQUEADO; (d) escenario 6 de la Historia 2 — el bloque de memoria es el mismo durante todo el bloqueo; (e) dos procesos bloqueados a la vez cumplen su E/S en paralelo; (f) varios eventos de E/S en un mismo proceso se aplican en orden; (g) `finBloqueo = fin del tick de la solicitud + duracion`
- [x] T033 [P] [US1] Escribir `src/test/java/gestor/motor/MetricasPrueba.java`: FR-027 retorno, FR-028 espera (incluye ticks en espera de memoria), FR-029 utilización = ticks CPU ocupada / instante final × 100 (escenario 1 → 100,0; escenario 2 → 60,0), FR-030 promedios (escenario 1 → espera 2,5; retorno 6,0); promedios `null` sin procesos terminados; utilización `null` en instante 0; invariante `retorno = tiempoRafaga + ticksES + tiempoEspera` para cada proceso terminado

### Implementation for User Story 1

- [x] T034 [US1] Crear `src/main/java/gestor/motor/Motor.java`: constructor `(Escenario, PoliticaAsignacion, Planificador)` que crea los `Proceso` en `PENDIENTE_LLEGADA` y el `MapaMemoria`; `void avanzarTick()` con los pasos 1–7 de data-model.md (admisión en orden: espera de memoria y luego llegadas del instante por `ordenArchivo`; un proceso que no cabe queda en `NUEVO` en la cola `esperaMemoria` con motivo "Sin memoria contigua suficiente"; fin de E/S; expropiado pendiente; despacho; ejecución del tick con acumulación de `ticksEsperaListo`, `ticksEsperaMemoria`, `ticksES` y Gantt; evaluación con precedencia ráfaga completa → E/S → quantum, liberando memoria al terminar); motivos de bitácora en español según data-model.md ("Llegada", "Memoria asignada [b–l]", "Despacho", "Quantum agotado", "Solicita E/S (n ticks)", "Fin de E/S", "Ráfaga completada"); `cambiarQuantum(int q)` (q ≥ 1, si no `IllegalArgumentException`); getters de solo lectura `instanteActual()`, `finalizada()`, `ticksCpuOcupada()`, `procesos()`, `bitacora()`, `gantt()`, `mapaMemoria()` — hace pasar T031 y T032 (depende de Fase 2)
- [x] T035 [US1] Crear `src/main/java/gestor/motor/Metricas.java`: record `(Double esperaPromedio, Double retornoPromedio, Double utilizacionCpu, int terminados, int total)` con fábrica `calcular(List<Proceso>, int ticksCpuOcupada, int instanteActual)`; documentar en Javadoc las fórmulas FR-027–FR-030 exactamente como en la constitución; método `Metricas Motor.metricas()` — hace pasar T033

**Checkpoint**: US1 completa — el motor reproduce los escenarios de referencia de la Historia 1 vía `.\scripts\probar.ps1`.

**Verificación TDD de US1**: se escribieron primero 28 pruebas nuevas de T031–T033 y se
comprobó el estado rojo (código 1 por ausencia de `Motor` y `Metricas`). Tras T034–T035,
la batería completa reporta `75 pruebas, 0 fallidas` y código de salida 0. Se verificaron
los Gantt y las métricas de referencia, la precedencia de eventos, el quantum fijado por turno,
la E/S en paralelo, el determinismo y la invariante del tiempo de retorno.

**Consultas de US1**: `bitacora()` y `gantt()` devuelven listas inmutables de registros;
`procesos()` devuelve una lista inmodificable de copias de los PCB y `mapaMemoria()` devuelve
una copia separada. Modificar estas copias no altera el motor; las consultas previas se
mantienen estables entre ticks. La admisión básica se amplió en T036–T037 con FR-015a.

---

## Phase 4: User Story 2 - Administrar la RAM con partición variable Best-Fit / First-Fit (Priority: P1)

**Goal**: integración completa de la memoria en el motor: cola de Espera de Memoria, admisión en el mismo instante de la liberación y límite de adelantamientos anti-inanición (FR-015a).

**Independent Test**: escenarios con RAM pequeña y fragmentación, verificando instante de admisión, bloque asignado, contadores de adelantamientos e invariantes del mapa en cada tick.

### Tests for User Story 2 ⚠️ (escribir primero, deben fallar)

- [x] T036 [P] [US2] Escribir `src/test/java/gestor/motor/MotorAdmisionMemoriaPrueba.java`: (a) escenario 4 — proceso de 60 MB con 70 MB libres pero mayor hueco de 40 MB queda en NUEVO y `fragmentacionExterna()` = 30; (b) escenario 5 — al terminar un proceso, el que espera se admite en ese mismo instante (paso 1) y pasa a LISTO; (c) cada admisión de un proceso posterior incrementa en 1 los `adelantamientos` de todos los anteriores que siguen esperando (incluidos adelantamientos por recién llegados); (d) escenario 7 — con G (80 MB) con 5 adelantamientos, un proceso de 10 MB que cabe (en espera o recién llegado) no se admite hasta que G entre, y el recién llegado se coloca al final de la Espera de Memoria; (e) procesos anteriores a G en la cola sí pueden admitirse; (f) `maxAdelantamientos` = 2 configurado en el escenario cambia el umbral; (g) tiempo de espera de un proceso incluye los ticks en Espera de Memoria; (h) tras cada tick de un escenario de 8 procesos se cumplen las invariantes de `InvariantesMemoria.verificar` (SC-007); (i) la memoria de un proceso TERMINADO queda liberada y fusionada; (j) el mismo escenario que produce los huecos [0–49] y [70–99] da [70–94] con `politicaMemoria` BEST_FIT y [0–24] con FIRST_FIT (escenarios 1 y 8), construyendo el motor con `FabricaRecursos.crearPolitica`

### Implementation for User Story 2

- [x] T037 [US2] Reemplazar la admisión básica de `src/main/java/gestor/motor/Motor.java` por el algoritmo de research.md §7: lista de candidatos (espera de memoria por llegada + llegadas del instante por `ordenArchivo`), bandera `bloqueada`; si cabe → asignar con la política, `NUEVO→LISTO`, encolar y sumar 1 a `adelantamientos` de cada candidato anterior aún en espera; si alguno alcanza `maxAdelantamientos` → `bloqueada = true`; si no cabe y su contador ≥ `maxAdelantamientos` → `bloqueada = true`; los bloqueados por la bandera quedan/entran en `esperaMemoria` en orden; exponer `boolean esBloqueante(Proceso)` — hace pasar T036

**Checkpoint**: US1 y US2 completas — el núcleo de SO está verificado de forma independiente de cualquier interfaz.

**Verificación TDD de US2**: T036 añade 12 pruebas. Con la consulta `esBloqueante` disponible
y la admisión anterior se observaron `87 pruebas, 5 fallidas` (código 1) por ausencia del
cálculo de adelantamientos. Tras T037, la batería completa reporta `87 pruebas, 0 fallidas`
y código de salida 0, conservando sin cambios las 75 pruebas anteriores.

Se verificaron fragmentación externa, admisión en el instante de liberación, adelantamientos
desde la cola y desde nuevas llegadas, límites 5 y 2, bloqueo dentro del mismo recorrido,
admisión de anteriores al protegido y reanudación ordenada de los posteriores al entrar este.
Las invariantes se comprueban tras cada tick, incluido un escenario de ocho procesos.
Best-Fit y First-Fit reproducen las asignaciones de los escenarios 1 y 8 de la Historia 2.

---

## Phase 5: User Story 5 - Cargar y validar el escenario desde `config.json` (Priority: P1)

**Goal**: lector JSON propio y validación completa del escenario según `contracts/config-json.md`, con arranque del programa que rechaza archivos inválidos (código 1).

**Independent Test**: archivos válidos producen el `Escenario` esperado; cada archivo de `src/test/recursos/invalidos/` se rechaza con mensaje que nombra proceso, campo y motivo.

### Tests for User Story 5 ⚠️ (escribir primero, deben fallar)

- [x] T038 [P] [US5] Escribir `src/test/java/gestor/json/LectorJsonPrueba.java`: objetos (como `LinkedHashMap` que conserva el orden), arreglos, cadenas con escapes (`\"`, `\\`, `\n`, `\uXXXX`, tildes UTF-8), enteros (`Long`), negativos, decimales y exponentes (`Double`), `true`/`false`/`null`, espacios; errores con `ErrorJson` que informa "línea L, columna C" para: coma final, llave sin cerrar, cadena sin cerrar, contenido sobrante tras el valor raíz, documento vacío
- [x] T039 [P] [US5] Crear los archivos inválidos de la batería SC-002 en `src/test/recursos/invalidos/`: `json-mal-formado.json`, `procesos-vacio.json`, `sin-procesos.json`, `campo-faltante.json` (sin `tiempoRafaga`), `tipo-invalido.json` (`memoria: "200"`), `llegada-negativa.json`, `rafaga-cero.json`, `id-vacio.json`, `id-duplicado.json` (P1 dos veces), `memoria-excede.json` (2048 MB con total 1024), `enquantum-fuera-rango.json` (`enQuantum` = `tiempoRafaga`), `enquantum-no-creciente.json`, `duracion-cero.json`, `quantum-cero.json`, `max-adelantamientos-cero.json`, `politica-desconocida.json` (`"WORST_FIT"`), `planificador-desconocido.json` (`"FCFS"`); y válidos `src/test/recursos/validos/completo.json` (con `configuracion` completa, incluido `"politicaMemoria": "FIRST_FIT"`, prioridad y E/S) y `src/test/recursos/validos/minimo.json` (sin `configuracion` ni campos opcionales)
- [x] T040 [P] [US5] Escribir `src/test/java/gestor/entrada/LectorEscenarioPrueba.java`: `completo.json` produce los valores exactos; `minimo.json` aplica defectos 1024 MB, quantum 1, `maxAdelantamientos` 5, `BEST_FIT`, `ROUND_ROBIN`, `bloqueosES` vacía, `prioridad` null; cada archivo inválido de T039 lanza `ErrorConfiguracion` cuyo mensaje empieza por "Error en config.json:" y contiene la ubicación (p. ej. `procesos[1] (id "P1")`) y el motivo de `contracts/config-json.md`; un archivo con dos errores informa ambos; un campo desconocido no es error y genera una advertencia; archivo inexistente → `ErrorConfiguracion` "no se encontró el archivo <ruta>"

### Implementation for User Story 5

- [x] T041 [US5] Crear `src/main/java/gestor/json/ErrorJson.java` (excepción con `linea`, `columna`) y `src/main/java/gestor/json/LectorJson.java`: analizador recursivo descendente `static Object leer(String texto)` que devuelve `Map<String,Object>`/`List<Object>`/`String`/`Long`/`Double`/`Boolean`/`null` — hace pasar T038
- [x] T042 [US5] Crear `src/main/java/gestor/entrada/ErrorConfiguracion.java` (`extends RuntimeException`, para poder lanzarse desde un `Supplier<Escenario>`; con `List<String> errores`; `getMessage()` los une con saltos de línea) y `src/main/java/gestor/entrada/LectorEscenario.java`: `Escenario leer(Path ruta)` y `Escenario leerTexto(String json)` que validan todas las reglas de la tabla de `contracts/config-json.md` (entre otras: "`id` no vacío, único", "`tiempoLlegada` ≥ 0", "`tiempoRafaga` ≥ 1", "`memoria` 1 … `memoriaTotal`", "`enQuantum` 1 … `tiempoRafaga`−1, estrictamente creciente", "`duracion` ≥ 1", "`memoriaTotal`, `quantum`, `maxAdelantamientos` ≥ 1", "`politicaMemoria` `BEST_FIT` o `FIRST_FIT`", "`planificador` `ROUND_ROBIN`" usando `desdeCodigo` y `valoresValidos()`, "al menos 1 proceso"), acumulan todos los errores antes de lanzar, asignan `ordenArchivo` y devuelven advertencias por campos desconocidos mediante `List<String> advertencias()` — hace pasar T040 (depende de T041)
- [x] T043 [US5] Crear `src/main/java/gestor/Principal.java`: interpreta `--config <ruta>` (defecto `config.json`) y `--puerto <n>` (defecto 8080; entero 0–65535, si no, error de uso); carga el escenario con `LectorEscenario`; ante `ErrorConfiguracion` imprime cada error en `System.err` y termina con `System.exit(1)`; imprime advertencias y un resumen ("Escenario cargado: N procesos, RAM M MB, quantum Q, política P, planificador A") (el arranque del servidor se añade en T055)
- [x] T044 [P] [US5] Crear los escenarios de `ejemplos/`: `referencia-rr.json` (escenario 1 de la Historia 1), `referencia-es.json` (escenario 2 de la Historia 1), `best-fit.json` (provoca huecos [0–49] y [70–99] y luego admite un proceso de 25 MB que va a [70–94]), `first-fit.json` (idéntico con `"politicaMemoria": "FIRST_FIT"`; el proceso de 25 MB va a [0–24]), `inanicion.json` (un proceso de 80 MB adelantado 5 veces por procesos pequeños), `carga-50.json` (50 procesos con E/S para SC-004) y `config.json` didáctico (6 procesos con E/S y espera de memoria); copiar `ejemplos/config.json` a `config.json` en la raíz

**Checkpoint**: `.\scripts\iniciar.ps1 -Config .\src\test\recursos\invalidos\id-duplicado.json` muestra el error y termina con código 1.

**Verificación TDD de US5**: se escribieron antes de la implementación las pruebas del lector
JSON y del escenario, junto con los 17 archivos inválidos y los 2 válidos. Se confirmó el rojo
por ausencia de los lectores y se verificaron también fallos de uso y advertencias del arranque
antes de corregirlos. La batería completa reporta `119 pruebas, 0 fallidas` (código 0):
87 pruebas anteriores intactas y 32 nuevas, incluidas las de `Principal` como proceso separado
y las que cargan los ejemplos y los ejecutan en el motor.

Se comprobaron tipos y rangos, valores por defecto, acumulación de errores, advertencias en
todos los niveles, UTF-8, posiciones de error, argumentos de arranque y códigos de salida.
Los ejemplos RR y E/S reproducen SC-001; la batería de 17 inválidos verifica SC-002.
`carga-50.json` contiene 50 procesos con E/S y el escenario didáctico contiene 6 procesos
con E/S y espera de memoria. El `config.json` raíz es una copia exacta del didáctico.
El arranque de T043 carga, valida e informa el resumen; el servidor se incorpora en T055.

---

## Phase 6: User Story 3 - Visualizar y controlar la simulación en el dashboard web (Priority: P2)

**Goal**: controlador en tiempo real (iniciar, pausar, reiniciar, velocidad, quantum, RAM, política de memoria), API HTTP de `contracts/api-http.md` y dashboard HTML/CSS/JS Vanilla.

**Independent Test**: pruebas del controlador y de la API con `java.net.http.HttpClient` contra el servidor en un puerto libre; verificación manual del dashboard con `quickstart.md` §2–3.

### Tests for User Story 3 ⚠️ (escribir primero, deben fallar)

- [ ] T045 [P] [US3] Escribir `src/test/java/gestor/json/EscritorJsonPrueba.java`: serializa `Map` (orden de inserción), `List`, `String` con escapes (`"`, `\`, control `\u0001`, salto de línea) y tildes sin escapar, enteros, decimales (`2.5`, `100.0`), booleanos y `null`; ida y vuelta con `LectorJson`
- [ ] T046 [P] [US3] Escribir `src/test/java/gestor/motor/MotorInstantaneaPrueba.java`: la `Instantanea` refleja instante, colas (`nuevos` con `adelantamientos` y `bloqueante`, `listos` en orden, `ejecucion` con `ticksQuantumUsados` o null, `bloqueados` con `ticksRestantesES`, `terminados`), procesos con bloque `[base, limite, tamano]` o null, `bloqueante` (verdadero solo en NUEVO con adelantamientos ≥ límite) y `ticksRestantesES` (solo en BLOQUEADO; null en otro caso), mapa de memoria con `libre`, `mayorHueco`, `fragmentacionExterna`, Gantt, bitácora y métricas; es inmutable (las listas lanzan `UnsupportedOperationException`) y no cambia al avanzar el motor
- [ ] T047 [P] [US3] Escribir `src/test/java/gestor/motor/ControladorSimulacionPrueba.java` usando `avanzarManual()` (sin hilo): tabla de acciones de data-model.md (iniciar desde DETENIDA/PAUSADA; pausar solo EN_CURSO; iniciar en FINALIZADA lanza `IllegalStateException`); `cambiarMemoriaTotal` rechazado fuera de DETENIDA ("La capacidad de RAM solo puede modificarse antes de iniciar") y cuando un proceso no cabría (nombra el proceso); `cambiarPoliticaMemoria` aceptado en DETENIDA (el motor recreado usa la nueva política) y rechazado fuera de DETENIDA ("La política de memoria solo puede modificarse antes de iniciar"); `cambiarVelocidad` fuera de 50–2000 rechazado; `cambiarQuantum(0)` rechazado; `reiniciar()` vuelve a instante 0 DETENIDA y vacía Gantt/bitácora; conserva solo los valores de quantum, RAM y política **modificados desde la interfaz** y toma del `config.json` releído los no modificados (si el archivo cambia el quantum y el usuario nunca lo tocó, se usa el del archivo); si la RAM conservada no aloja algún proceso del archivo releído, usa la RAM del archivo y deja `aviso`; si el proveedor del escenario falla, reinicia con el último escenario válido y deja `aviso` con el error; pasa a FINALIZADA cuando el motor termina; SC-006: con el hilo real a 50 ms, pausar/reanudar varias veces y cambiar la velocidad produce la misma bitácora que la ejecución manual
- [ ] T048 [P] [US3] Escribir `src/test/java/gestor/interfaz/web/ApiHttpPrueba.java` con `HttpClient` contra `ServidorWeb` en puerto 0 (aleatorio): `GET /api/estado` 200 con `Content-Type: application/json; charset=utf-8` y los campos de `contracts/api-http.md`; `GET /api/estado?desdeEvento=n` devuelve en `bitacora` solo los eventos con índice ≥ n y `totalEventos` con el total; `desdeEvento` negativo o no numérico → 400 `VALOR_INVALIDO`; `GET /api/proceso/P1` 200; `GET /api/proceso/P9` 404 `PROCESO_NO_EXISTE` con mensaje "El proceso 'P9' no existe"; `POST /api/control/iniciar|pausar|reiniciar`; 409 `TRANSICION_INVALIDA`; `POST /api/configuracion` con valor inválido → 400 `VALOR_INVALIDO` sin aplicar ningún campo, RAM en curso → 409 `MEMORIA_NO_EDITABLE`, RAM insuficiente → 409 `MEMORIA_INSUFICIENTE`, `politicaMemoria: "FIRST_FIT"` en DETENIDA → 200 y `configuracion.politicaMemoria` = `FIRST_FIT`, política desconocida → 400 `VALOR_INVALIDO`, política en curso → 409 `MEMORIA_NO_EDITABLE`; `configuracion.planificador` = `ROUND_ROBIN`; `GET /api/estado` con POST → 405; `GET /` sirve `index.html`; ruta desconocida → 404; el servidor solo escucha en loopback

### Implementation for User Story 3

- [ ] T049 [US3] Crear `src/main/java/gestor/json/EscritorJson.java`: `static String escribir(Object valor)` para `Map`, `List`, `String`, `Number`, `Boolean`, `null` — hace pasar T045
- [ ] T050 [US3] Crear `src/main/java/gestor/motor/Instantanea.java` con records anidados inmutables (`ProcesoEnEspera`, `ProcesoEnEjecucion`, `ProcesoBloqueado`, `ResumenProceso` —con `boolean bloqueante` e `Integer ticksRestantesES`—, `BloqueMemoria`, `EstadoMemoria`, `Colas`) y el método `Instantanea Motor.instantanea()` en `src/main/java/gestor/motor/Motor.java`. El motor no conoce el modo, la velocidad ni el aviso: los genera con valores neutros (`modo` null, `velocidadMs` 0, `aviso` null) y `Instantanea` ofrece `Instantanea con(ModoSimulacion modo, int velocidadMs, String aviso)`, que devuelve una copia con esos campos (la usa el controlador en T051) — hace pasar T046
- [ ] T051 [US3] Crear `src/main/java/gestor/motor/ModoSimulacion.java` (enum `DETENIDA, EN_CURSO, PAUSADA, FINALIZADA`) y `src/main/java/gestor/motor/ControladorSimulacion.java`: constructor `(Supplier<Escenario> proveedor)`; todas las operaciones `synchronized` sobre el mismo monitor; hilo daemon "reloj-simulacion" que en EN_CURSO llama `avanzarTick()` cada `velocidadMs` (defecto 500, rango 50–2000) y pasa a FINALIZADA al terminar; el motor se construye con `FabricaRecursos.crearPolitica/crearPlanificador` según la configuración vigente; `iniciar`, `pausar`, `reiniciar` (captura cualquier `RuntimeException` del proveedor, incluida `ErrorConfiguracion`, conserva el último escenario válido y guarda su mensaje en `aviso`; registra qué valores de quantum, RAM y política se modificaron desde la interfaz y solo esos se superponen al escenario releído, según el escenario 8 de la Historia 3), `cambiarQuantum`, `cambiarMemoriaTotal`, `cambiarPoliticaMemoria` (solo DETENIDA; recrea el motor), `cambiarVelocidad`, `avanzarManual()` (para pruebas), `instantanea()` que devuelve `motor.instantanea().con(modo, velocidadMs, aviso)`; `detener()` para cerrar el hilo — hace pasar T047 (depende de T050)
- [ ] T052 [US3] Crear `src/main/java/gestor/interfaz/web/ConvertidorInstantanea.java`: convierte `Instantanea` y su `ResumenProceso` a `Map`/`List` con los nombres de campo exactos de `contracts/api-http.md` (`instante`, `modo`, `configuracion` con `politicaMemoria` y `planificador`, `colas`, `procesos` con `bloqueante` y `ticksRestantesES`, `memoria`, `gantt`, `bitacora` a partir de `desdeEvento`, `totalEventos`, `metricas`, `aviso`)
- [ ] T053 [US3] Crear `src/main/java/gestor/interfaz/web/ManejadorEstaticos.java` (sirve `/`→`/web/index.html`, `/estilos.css`, `/app.js` desde el classpath con el tipo MIME y `charset=utf-8`; 404 en otro caso) y `src/main/java/gestor/interfaz/web/ManejadorApi.java` (rutas, métodos, códigos y cuerpos de error `{ "error", "mensaje" }` de `contracts/api-http.md`; valida todo el cuerpo de `/api/configuracion` (incluido `politicaMemoria` con `PoliticaMemoria.desdeCodigo`) antes de aplicar; lee el cuerpo como UTF-8 con `LectorJson`; interpreta el parámetro `desdeEvento` de `/api/estado`, por defecto 0)
- [ ] T054 [US3] Crear `src/main/java/gestor/interfaz/web/ServidorWeb.java`: `HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0)`, ejecutor `Executors.newFixedThreadPool(4)`, contextos `/api/` y `/`; `iniciar()`, `detener()`, `int puerto()` (para el puerto 0); si el puerto está ocupado lanza excepción con "El puerto <n> ya está en uso" — hace pasar T048 (depende de T049, T051–T053)
- [ ] T055 [US3] Ampliar `src/main/java/gestor/Principal.java`: crea `ControladorSimulacion` con un proveedor que relee el archivo `--config` en cada reinicio, arranca `ServidorWeb`, imprime "Simulador disponible en http://localhost:<puerto>"; si el puerto está ocupado imprime el error y `System.exit(1)`
- [ ] T056 [P] [US3] Crear `src/main/resources/web/index.html` (HTML5, `lang="es"`, UTF-8): cabecera con instante y modo; panel de control (botones Iniciar/Reanudar, Pausar, Reiniciar; deslizante de velocidad 50–2000 ms con valor visible; campos numéricos de quantum y RAM y selector de política de memoria (Best-Fit / First-Fit) con botón Aplicar; indicador de solo lectura del planificador vigente; zona de mensajes); secciones para las colas Nuevos/Espera de RAM, Listos, En CPU, Bloqueados, Terminados; mapa de RAM; Gantt; tabla de métricas por proceso y resumen global; bitácora
- [ ] T057 [P] [US3] Crear `src/main/resources/web/estilos.css`: diseño en rejilla adaptable, colores por proceso (generados en JS a partir del id), huecos de RAM rayados, tramos ociosos del Gantt en gris, estados de botón deshabilitado, mensajes de error visibles, legible en pantallas de 1280 px y en proyector
- [ ] T058 [US3] Crear `src/main/resources/web/app.js` (JavaScript Vanilla, sin dependencias): sondeo `fetch('/api/estado?desdeEvento=' + eventosRecibidos)` cada 200 ms, acumulando la bitácora en el navegador (si `totalEventos` es menor que los eventos guardados, por un Reiniciar, vacía la copia local y vuelve a pedir desde 0); renderiza colas, CPU (con ticks de quantum usados), mapa de RAM proporcional con base–límite por bloque y tamaño de huecos, Gantt acumulativo con escala de instantes, métricas (2 decimales para promedios, 1 decimal para % CPU, separador decimal coma), bitácora (los más recientes primero); acciones `POST` a `/api/control/*` y `/api/configuracion`; habilita/deshabilita botones, el campo de RAM y el selector de política según `modo` (solo editables en DETENIDA); muestra la política vigente en el título del mapa de RAM y, junto al mapa, `libre`, `mayorHueco` y `fragmentacionExterna`; en la cola Nuevos/Espera de RAM muestra por proceso la memoria requerida, el contador de adelantamientos y la causa de la espera ("necesita X MB; mayor hueco contiguo Y MB", señalando "fragmentación externa" si la memoria libre total alcanzaría pero ningún hueco contiguo), y resalta al proceso bloqueante (FR-015a); muestra `mensaje` de errores y `aviso`; si el servidor no responde, muestra "Sin conexión con el simulador" y reintenta

**Checkpoint**: US3 completa — `quickstart.md` §2 y §3 se cumplen en el navegador.

---

## Phase 7: User Story 4 - Consultar un proceso desde PowerShell (Priority: P2)

**Goal**: script `gestor.ps1` según `contracts/cli-gestor.md` que consulta la API local.

**Independent Test**: con el simulador en ejecución, el script de prueba invoca `gestor.ps1` en cada caso de la tabla del contrato y verifica la salida y `$LASTEXITCODE`.

**Dependencia**: requiere la API de US3 (T054–T055).

### Tests for User Story 4 ⚠️ (escribir primero, deben fallar)

- [ ] T059 [P] [US4] Crear `scripts/probar-cli.ps1`: compila y arranca `java -jar build/gestor-procesos.jar --config ejemplos/referencia-rr.json --puerto 18080` en segundo plano, espera (máx. 10 s) a que `/api/estado` responda y verifica, en este orden y con `-Puerto 18080`: (1) sin argumento → código 1 y texto "Uso:"; (2) `P9` → código 2 y "no existe"; (3) en el instante 0 sin iniciar, `P2` (llega en 1) → código 0 y "todavía no ha llegado al sistema (llega en el instante 1)"; (4) `POST /api/configuracion {"velocidadMs":2000}`, `POST /api/control/iniciar`, esperar hasta que `instante` ≥ 2 y `POST /api/control/pausar`; entonces `P1` → código 0 y la ficha contiene "Estado", "Quantums (ticks) consumidos", "Quantums (ticks) restantes", "Bloque de RAM" y "Tiempo de espera"; (5) `POST /api/configuracion {"velocidadMs":50}`, iniciar y esperar a `modo` = FINALIZADA (máx. 10 s); `P1` → código 3 y "ya finalizó su ciclo de vida (terminó en el instante 6)"; (6) cada invocación de los casos 2–5 tarda < 2 s, medido con `Measure-Command` (SC-005); (7) tras detener el proceso Java, `P1` → código 4 y "no se pudo conectar". Imprime un resumen y termina con código ≠ 0 si algo falla; siempre detiene el proceso Java al final (bloque `finally`)

### Implementation for User Story 4

- [ ] T060 [US4] Crear `gestor.ps1` en la raíz (**UTF-8 con BOM**, compatible con PowerShell 5.1 y 7): `param([Parameter(Position=0)][string]$IdProceso, [int]$Puerto = 8080)`; sin argumento → uso y `exit 1`; `Invoke-WebRequest -UseBasicParsing -TimeoutSec 2` a `http://localhost:$Puerto/api/proceso/<id codificado>`; decodifica el cuerpo como UTF-8 desde los bytes (`RawContentStream`) y `ConvertFrom-Json`; en 404 lee el cuerpo de la respuesta de error → `exit 2`; `TERMINADO` → mensaje con `instanteFinalizacion` → `exit 3`; error de conexión → `exit 4`; otro código HTTP → `exit 5`; `PENDIENTE_LLEGADA` → mensaje con instante de llegada y `exit 0`; en otro caso imprime la ficha con el formato exacto de `contracts/cli-gestor.md` (etiquetas de estado en español, "sin asignar (esperando memoria)", línea de E/S restante en Bloqueado, línea de bloqueante) y `exit 0`; errores por `[Console]::Error.WriteLine` — hace pasar T059

**Checkpoint**: todas las historias funcionan de forma independiente.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: documentación, verificación de la constitución y validación final

- [ ] T061 [P] Crear `scripts/verificar-capas.ps1`: falla (código 1) si algún archivo de `src/main/java/gestor/{modelo,recursos,motor}` importa `gestor.interfaz`, `gestor.entrada`, `gestor.json`, `com.sun.net.httpserver`, `java.net` o `java.io`/`java.nio.file`, si `gestor/recursos` importa `gestor.motor`, si `gestor/interfaz` importa `gestor.entrada`, si algún archivo distinto de `gestor/Principal.java` importa `gestor.entrada`, o si cualquier archivo importa paquetes fuera de `java.`, `javax.`, `com.sun.net.httpserver` y `gestor.`; invocarlo al final de `scripts/probar.ps1`
- [ ] T062 [P] Crear `README.md` en español: propósito, requisitos (solo JDK 21), compilar/probar/iniciar, formato de `config.json` (enlace a `specs/001-simulador-procesos-memoria/contracts/config-json.md`), uso de `gestor.ps1`, modelo de tiempo y orden de eventos, fórmulas de métricas, estructura de capas
- [ ] T063 [P] Revisar que todas las clases y métodos públicos de `src/main/java/gestor/` tengan Javadoc en español y que no queden identificadores, comentarios o mensajes en inglés (salvo palabras reservadas y APIs del JDK)
- [ ] T064 Prueba de rendimiento SC-004 en `src/test/java/gestor/motor/RendimientoPrueba.java`: con `ejemplos/carga-50.json`, 2 000 llamadas a `avanzarTick()` + `instantanea()` + `EscritorJson` tardan en promedio < 10 ms por tick; la respuesta de un sondeo incremental (eventos de los últimos 4 ticks) ocupa < 500 KB; y la respuesta completa (`desdeEvento=0`) en el último tick se serializa en < 200 ms
- [ ] T065 Ejecutar la validación completa de `specs/001-simulador-procesos-memoria/quickstart.md` (§1–§5), incluido `scripts/probar-cli.ps1`, más una verificación manual de SC-004: con `ejemplos/carga-50.json` a 50 ms por tick, en las herramientas de desarrollo del navegador cada respuesta de `/api/estado` llega en < 1 s y el instante mostrado nunca se retrasa más de 1 s respecto al real. Corregir cualquier desviación

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA todas las historias.
- **US1 (Phase 3)**: depende de Fase 2.
- **US2 (Phase 4)**: depende de US1 (modifica la admisión de `Motor.java`).
- **US5 (Phase 5)**: depende solo de Fase 2 (modelo); puede ir en paralelo con US1/US2.
- **US3 (Phase 6)**: depende de US1 (motor) y US5 (`Principal`, carga del escenario); se beneficia de US2.
- **US4 (Phase 7)**: depende de US3 (API HTTP).
- **Polish (Phase 8)**: depende de todas las historias.

### Grafo de historias

```text
Setup → Fundacional ─┬─ US1 → US2 ─┐
                     └─ US5 ───────┴─ US3 → US4 → Pulido
```

### Within Each User Story

- Las pruebas se escriben primero y deben FALLAR antes de implementar (Principio VI).
- Modelo → recursos → motor → interfaz.
- Ejecutar `.\scripts\probar.ps1` al cerrar cada tarea de implementación.

### Parallel Opportunities

- Setup: T002–T006 en paralelo.
- Fundacional: pruebas T008–T013 en paralelo; luego T014–T016, T017, T021, T022, T026, T029, T030 en paralelo; T023 y T024 en paralelo.
- US1: T031, T032, T033 en paralelo.
- US5 completa en paralelo con US1/US2 (archivos distintos); dentro de US5, T038, T039, T040, T044 en paralelo.
- US3: pruebas T045–T048 en paralelo; T056 y T057 en paralelo con el backend.
- Pulido: T061, T062, T063 en paralelo.

---

## Parallel Example: User Story 1

```text
# Pruebas de US1 en paralelo (archivos distintos):
Task: "Escribir MotorRoundRobinPrueba en src/test/java/gestor/motor/MotorRoundRobinPrueba.java"
Task: "Escribir MotorEntradaSalidaPrueba en src/test/java/gestor/motor/MotorEntradaSalidaPrueba.java"
Task: "Escribir MetricasPrueba en src/test/java/gestor/motor/MetricasPrueba.java"
```

## Parallel Example: User Story 5 (en paralelo con US1)

```text
Task: "Escribir LectorJsonPrueba en src/test/java/gestor/json/LectorJsonPrueba.java"
Task: "Crear batería de inválidos en src/test/recursos/invalidos/"
Task: "Escribir LectorEscenarioPrueba en src/test/java/gestor/entrada/LectorEscenarioPrueba.java"
Task: "Crear escenarios en ejemplos/"
```

## Parallel Example: User Story 3

```text
Task: "Escribir EscritorJsonPrueba", "MotorInstantaneaPrueba", "ControladorSimulacionPrueba", "ApiHttpPrueba"
Task: "Crear src/main/resources/web/index.html" y "src/main/resources/web/estilos.css"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1 (Setup) y Fase 2 (Fundacional).
2. Fase 3 (US1): el motor RR + E/S verificado con los escenarios de referencia.
3. **STOP and VALIDATE**: `.\scripts\probar.ps1` en verde → ya sirve para corregir ejercicios de clase.

### Incremental Delivery

1. Setup + Fundacional → base lista.
2. US1 → motor RR + E/S (MVP).
3. US2 → memoria completa con anti-inanición.
4. US5 → escenarios desde `config.json`.
5. US3 → dashboard en tiempo real (primera versión utilizable por estudiantes).
6. US4 → CLI PowerShell.
7. Pulido → verificación de capas, README, rendimiento, quickstart.

---

## Notes

- [P] = archivos distintos y sin dependencias pendientes.
- Las etiquetas [USn] trazan cada tarea con su historia de la spec.
- Verificar que cada prueba falle antes de implementar.
- Hacer commit tras cada tarea o grupo lógico.
- Evitar tareas vagas, conflictos en el mismo archivo y dependencias entre historias no documentadas.
