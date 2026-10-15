# Feature Specification: Simulador de Gestión de Procesos y Memoria RAM

**Feature Branch**: `001-simulador-procesos-memoria`

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "Especificar detalladamente los requerimientos funcionales, contratos de
datos y criterios de aceptación para el Simulador de Gestión de Procesos y Memoria RAM: carga de
procesos desde `config.json`, ciclo de vida de 5 estados con Round Robin expulsivo y E/S simulada,
memoria de partición variable con Best-Fit y fusión de huecos, dashboard web en tiempo real (colas,
mapa de RAM, Gantt, controles), CLI PowerShell `.\gestor.ps1 <idproceso>` y métricas finales."

## Clarifications

### Session 2026-10-15

- Q: Cuando el primero de la cola de Espera de Memoria no cabe, ¿pueden entrar antes procesos
  posteriores que sí caben? → A: Sí, pero con un límite de 5 adelantamientos por proceso en
  espera (anti-inanición); configurable mediante `configuracion.maxAdelantamientos`.
- Q: ¿El tiempo de espera incluye los ticks en Espera de Memoria o solo los ticks en Listo? → A:
  Incluye ambos (espera = ticks en Listo + ticks en Espera de Memoria = retorno − ráfaga − E/S),
  sin desglose adicional.
- Q: ¿El dashboard ofrece reinicio de la simulación? → A: Sí, botón Reiniciar que vuelve al
  instante 0 con el mismo `config.json`, conserva quantum y RAM elegidos en la interfaz y deja la
  simulación detenida.
- Q: Si en el mismo instante llega un proceso, otro vuelve de E/S y otro agota su quantum, ¿en qué
  orden entran a Listos? → A: Recién llegado, luego retornado de E/S, al final el expropiado.

## Glosario y Modelo de Tiempo

- **Tick**: unidad atómica de tiempo de la simulación. Toda magnitud temporal del archivo de
  configuración (llegada, ráfaga, momento y duración de E/S) se expresa en ticks.
- **Quantum**: cantidad máxima de ticks consecutivos que un proceso puede usar la CPU en un turno.
  Por defecto vale 1 tick.
- **Instante t**: el comienzo del tick número `t` (la simulación empieza en el instante 0).
- **Orden de eventos dentro de cada instante t** (regla determinista y obligatoria):
  1. Se intenta admitir en memoria, en orden de llegada, a los procesos que ya estaban en
     Espera de Memoria.
  2. Llegan los procesos con `tiempoLlegada = t` (en el orden en que aparecen en el archivo) y se
     intenta admitirlos; si caben pasan a Listo, si no, a Espera de Memoria.
  3. Los procesos cuya E/S termina en el instante t pasan de Bloqueado a Listo.
  4. El proceso expropiado al final del tick anterior (quantum agotado) se coloca al final de Listos.
  5. Si la CPU está libre, se despacha el primer proceso de la cola de Listos.
  6. Se ejecuta el tick: el proceso en CPU consume 1 tick de ráfaga.
  7. Al final del tick se evalúa, en este orden de precedencia: (a) ráfaga completada → Terminado y
     liberación de memoria; (b) alcanzó un `enQuantum` de E/S → Bloqueado; (c) agotó el quantum →
     expropiado.
- El cambio de contexto no consume tiempo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ejecutar una simulación de planificación Round Robin con E/S (Priority: P1)

Un estudiante prepara un archivo `config.json` con un lote de procesos, arranca el simulador y
obtiene la ejecución completa tick a tick con Round Robin expulsivo, incluyendo bloqueos por E/S,
hasta que todos los procesos terminan, junto con las métricas por proceso y globales.

**Why this priority**: es el núcleo didáctico del producto; sin un motor correcto ninguna
visualización tiene valor. Por sí solo permite comprobar ejercicios de clase.

**Independent Test**: cargar un escenario de referencia y comparar la secuencia de ejecución
(Gantt), los instantes de finalización y las métricas con los valores calculados a mano.

**Acceptance Scenarios**:

1. **Given** RAM de 100 MB, quantum 2, P1 (llegada 0, ráfaga 4, 30 MB) y P2 (llegada 1, ráfaga 3,
   20 MB) sin E/S, **When** la simulación se ejecuta hasta el final, **Then** el Gantt es
   P1[0–2), P2[2–4), P1[4–6), P2[6–7); P1 termina en 6 y P2 en 7; tiempos de retorno 6 y 6;
   tiempos de espera 2 y 3; espera promedio 2,5; retorno promedio 6,0; utilización de CPU 100 %.
2. **Given** quantum 1 y P1 (llegada 0, ráfaga 3, 10 MB, `bloqueosES` = [{enQuantum 1,
   duracion 2}]) como único proceso, **When** se ejecuta, **Then** P1 corre en el tick 0, está
   Bloqueado durante los ticks 1 y 2 (CPU ociosa), vuelve a Listo en el instante 3, corre los ticks
   3 y 4 y termina en 5; retorno 5, espera 0, utilización de CPU 60 %.
3. **Given** un proceso en ejecución con quantum 3 que alcanza su `enQuantum` tras 1 tick en su
   turno, **When** termina ese tick, **Then** abandona la CPU inmediatamente hacia Bloqueados sin
   agotar el quantum y conserva su bloque de memoria.
4. **Given** un proceso que completa su ráfaga exactamente al agotar su quantum, **When** termina
   el tick, **Then** pasa a Terminado (no se reencola en Listos).
5. **Given** en el mismo instante llega un proceso nuevo, otro vuelve de E/S y otro fue expropiado,
   **When** se arma la cola de Listos, **Then** el orden es: nuevo, retornado de E/S, expropiado.

---

### User Story 2 - Administrar la memoria RAM con partición variable Best-Fit (Priority: P1)

El estudiante observa cómo cada proceso recibe un bloque contiguo de RAM elegido con Best-Fit,
cómo los procesos que no caben esperan, y cómo al terminar un proceso su bloque se libera y se
fusiona con los huecos adyacentes.

**Why this priority**: la administración de memoria es la segunda mitad del objetivo del curso y
condiciona cuándo un proceso puede pasar a Listo.

**Independent Test**: ejecutar secuencias de asignación y liberación sobre una RAM de tamaño
conocido y comparar la lista de bloques y huecos (base, límite, tamaño) con la esperada.

**Acceptance Scenarios**:

1. **Given** RAM de 100 MB con huecos [0–49] (50 MB) y [70–99] (30 MB), **When** se admite un
   proceso de 25 MB, **Then** recibe el bloque [70–94] (el hueco más pequeño en que cabe) y queda
   el hueco [95–99].
2. **Given** dos huecos del mismo tamaño en que cabe el proceso, **When** se asigna, **Then** se
   elige el de menor dirección base.
3. **Given** bloques ocupados A [0–49], B [50–69] y C [70–89] y hueco [90–99], **When** terminan
   A y C y luego B, **Then** tras A y C los huecos son [0–49] y [70–99] (C fusionado con el hueco
   final), y tras B queda un único hueco [0–99].
4. **Given** un proceso de 60 MB que llega cuando el mayor hueco contiguo mide 40 MB aunque la
   memoria libre total sea 70 MB, **When** se intenta admitir, **Then** queda en Espera de Memoria
   y la interfaz indica la causa (fragmentación externa).
5. **Given** un proceso en Espera de Memoria, **When** otro proceso termina y la fusión resultante
   genera un hueco suficiente, **Then** el proceso en espera se admite en el mismo instante en que
   se libera la memoria (paso 1 del orden de eventos) y pasa a Listo.
7. **Given** un proceso G de 80 MB en Espera de Memoria que ya fue adelantado 5 veces, **When** llega
   o espera detrás de él un proceso de 10 MB que cabe, **Then** el de 10 MB no se admite hasta que G
   sea admitido.
6. **Given** un proceso Bloqueado por E/S, **When** transcurre su bloqueo, **Then** mantiene durante
   todo ese tiempo el mismo bloque de memoria.

---

### User Story 3 - Visualizar y controlar la simulación en el dashboard web (Priority: P2)

El estudiante o docente abre el dashboard en el navegador y ve en tiempo real las colas, el
proceso en CPU, el mapa de RAM, el Gantt acumulativo y las métricas; puede iniciar, pausar,
reanudar, ajustar la velocidad y modificar el quantum y la capacidad de RAM.

**Why this priority**: es el medio principal de enseñanza, pero depende de que el motor (P1) exista.

**Independent Test**: arrancar el simulador con un escenario, abrir el dashboard y verificar que
cada control produce el efecto descrito y que la información mostrada coincide con el estado del
motor en cada tick.

**Acceptance Scenarios**:

1. **Given** el simulador recién arrancado con un escenario cargado, **When** se abre el dashboard,
   **Then** se ve el instante 0, las colas vacías o con los procesos que llegan en 0, el mapa de
   RAM completamente libre y la simulación detenida.
2. **Given** la simulación detenida, **When** se pulsa Iniciar/Reanudar, **Then** el reloj avanza
   un tick cada intervalo indicado por el control de velocidad y la vista se actualiza en cada tick.
3. **Given** la simulación en curso, **When** se pulsa Pausar, **Then** el reloj se detiene en el
   tick actual y, al reanudar, continúa desde ese mismo estado sin pérdida ni repetición de ticks.
4. **Given** la simulación en curso, **When** se mueve el control de velocidad, **Then** el nuevo
   intervalo entre ticks se aplica sin reiniciar la simulación y sin alterar su resultado lógico.
5. **Given** la simulación en curso, **When** se cambia el quantum, **Then** el nuevo valor se aplica
   a partir del siguiente despacho de CPU.
6. **Given** la simulación sin iniciar (instante 0), **When** se cambia la capacidad de RAM,
   **Then** el mapa de memoria se redimensiona; **Given** la simulación ya iniciada, **When** se
   intenta cambiarla, **Then** el cambio se rechaza con un mensaje explicativo.
7. **Given** la simulación terminada, **When** se observa el dashboard, **Then** se muestran los
   procesos en Terminados, el Gantt completo (con tramos ociosos marcados) y el resumen de métricas.
8. **Given** una simulación en cualquier modo (en curso, pausada o finalizada), **When** se pulsa
   Reiniciar, **Then** se vuelve a leer el mismo `config.json`, la simulación vuelve al instante 0
   detenida, se vacían Gantt, bitácora y métricas, se conservan el quantum y la capacidad de RAM
   elegidos en la interfaz y la capacidad de RAM vuelve a ser editable.
9. **Given** que tras reiniciar algún proceso requiere más memoria que la nueva capacidad elegida,
   **When** se intenta cambiar la capacidad, **Then** el cambio se rechaza indicando el proceso que
   no cabría.

---

### User Story 4 - Consultar un proceso desde PowerShell (Priority: P2)

Mientras la simulación corre, el usuario ejecuta `.\gestor.ps1 P2` y obtiene en consola el estado
detallado de ese proceso.

**Why this priority**: permite inspección precisa y automatizable, exigida por la constitución,
pero no es imprescindible para ver la simulación.

**Independent Test**: con el simulador en ejecución, invocar el script para procesos en distintos
estados y para identificadores inexistentes, y comparar la salida con el estado del dashboard.

**Acceptance Scenarios**:

1. **Given** P2 en ejecución o en cualquier estado activo, **When** se ejecuta `.\gestor.ps1 P2`,
   **Then** la consola muestra: identificador, estado actual, ticks de CPU consumidos, ticks
   restantes, bloque de RAM ocupado (base–límite y tamaño, o "sin asignar" si está en Espera de
   Memoria) y tiempo de espera acumulado, y el script finaliza con código 0.
2. **Given** un identificador que no existe en el escenario, **When** se consulta, **Then** se
   muestra "El proceso '<id>' no existe" y el script finaliza con código distinto de cero.
3. **Given** un proceso que ya terminó, **When** se consulta, **Then** se muestra un mensaje que
   indica que el proceso ya finalizó su ciclo de vida (incluyendo su instante de finalización) y el
   script finaliza con código distinto de cero.
4. **Given** un proceso cuyo instante de llegada aún no se alcanza, **When** se consulta, **Then**
   se indica que el proceso todavía no ha llegado al sistema (y en qué instante llegará).
5. **Given** el simulador apagado, **When** se ejecuta el script, **Then** se informa que no se pudo
   conectar con el simulador y el script finaliza con código distinto de cero.
6. **Given** el script invocado sin argumento, **When** se ejecuta, **Then** muestra la forma de uso.

---

### User Story 5 - Cargar y validar el escenario desde `config.json` (Priority: P1)

El usuario define procesos y parámetros globales en un archivo JSON; el simulador lo valida al
arrancar y rechaza cualquier error con un mensaje preciso en español.

**Why this priority**: es la única vía de entrada de datos; sin ella no hay simulación.

**Independent Test**: arrancar el simulador con archivos válidos e inválidos y verificar que los
válidos se cargan íntegramente y los inválidos se rechazan indicando campo y motivo.

**Acceptance Scenarios**:

1. **Given** un `config.json` válido, **When** arranca el simulador, **Then** todos los procesos se
   cargan con sus atributos y la configuración global indicada (o los valores por defecto).
2. **Given** un archivo con un `id` repetido, **When** arranca, **Then** se rechaza indicando el
   `id` duplicado y no se inicia la simulación.
3. **Given** un proceso con `memoria` mayor que la capacidad total de RAM, **When** arranca,
   **Then** se rechaza indicando el proceso y que nunca podría alojarse.
4. **Given** un `enQuantum` fuera del rango 1 … `tiempoRafaga`−1, no creciente respecto al anterior,
   o una `duracion` menor que 1, **When** arranca, **Then** se rechaza indicando proceso, evento y
   motivo.
5. **Given** un archivo con JSON mal formado, **When** arranca, **Then** se informa la posición
   aproximada del error.

### Edge Cases

- CPU ociosa: si no hay procesos Listos en un tick, el tick se cuenta como ocioso, se marca en el
  Gantt y reduce la utilización de CPU.
- Ningún proceso llega en el instante 0: la simulación avanza ticks ociosos hasta la primera llegada.
- Procesos que llegan en el mismo instante: se procesan en el orden en que aparecen en el archivo.
- Varios procesos en Espera de Memoria: en cada instante se recorren en orden de llegada y se admite
  a todo aquel que quepa, aunque uno anterior más grande siga sin caber, salvo que ese anterior ya
  haya sido adelantado 5 veces (ver FR-015a).
- Un proceso recién llegado que cabe en memoria mientras existe un proceso en espera que ya alcanzó
  el límite de adelantamientos: no se admite y pasa al final de la Espera de Memoria.
- Un proceso con `tiempoRafaga` 1 o sin `bloqueosES`: no se bloquea nunca.
- E/S y fin de quantum en el mismo tick: prevalece la E/S (el proceso va a Bloqueados).
- Lista de procesos vacía: el archivo se rechaza con un mensaje.
- Quantum o capacidad de RAM ingresados como cero, negativos o no numéricos desde la interfaz: se
  rechazan sin modificar el valor vigente.
- Puerto 8080 ocupado: el simulador informa el error y termina sin iniciar la simulación.
- Consultas concurrentes (dashboard y CLI a la vez): siempre devuelven un estado consistente de un
  único instante.

## Requirements *(mandatory)*

### Functional Requirements

**Configuración y entrada de datos**

- **FR-001**: El sistema DEBE cargar el escenario al arrancar desde un archivo `config.json`
  (ruta por defecto en el directorio de trabajo, con posibilidad de indicar otra al iniciar).
- **FR-002**: Cada proceso DEBE definir: `id` (texto único, no vacío), `tiempoLlegada` (entero ≥ 0),
  `tiempoRafaga` (entero ≥ 1), `memoria` (entero ≥ 1, en MB), `prioridad` (entero, opcional, se
  almacena y muestra pero no afecta a Round Robin) y `bloqueosES` (lista opcional, vacía por
  defecto).
- **FR-003**: Cada evento de `bloqueosES` DEBE tener `enQuantum` (número de ticks de CPU acumulados
  por el proceso tras los cuales solicita la E/S; entero entre 1 y `tiempoRafaga`−1, estrictamente
  creciente dentro de la lista) y `duracion` (entero ≥ 1, ticks de bloqueo).
- **FR-004**: El archivo PUEDE incluir una configuración global con `memoriaTotal` (MB), `quantum`
  (ticks) y `maxAdelantamientos` (entero ≥ 1); si se omiten, se usan los valores por defecto
  1024 MB, 1 tick y 5 adelantamientos.
- **FR-005**: El sistema DEBE rechazar antes de iniciar cualquier archivo con JSON mal formado,
  campos obligatorios ausentes, tipos o rangos inválidos, `id` duplicados, lista de procesos vacía o
  procesos cuya memoria exceda la capacidad total, indicando en español el proceso, el campo y el
  motivo.

**Ciclo de vida y planificación de CPU**

- **FR-006**: Cada proceso DEBE estar en exactamente uno de estos estados: Nuevo/Espera de Memoria,
  Listo, En Ejecución, Bloqueado o Terminado; antes de su llegada se considera "Pendiente de
  llegada" y no forma parte de ninguna cola.
- **FR-007**: Solo se permiten las transiciones: Nuevo→Listo, Listo→En Ejecución,
  En Ejecución→Listo (quantum agotado), En Ejecución→Bloqueado (E/S), Bloqueado→Listo (fin de E/S)
  y En Ejecución→Terminado. Cualquier otra transición es un error del sistema.
- **FR-008**: El sistema DEBE planificar la CPU con Round Robin expulsivo: cola de Listos FIFO, una
  única CPU, quantum configurable (por defecto 1 tick), siguiendo el orden de eventos del Glosario.
- **FR-009**: Al completar los ticks indicados por `enQuantum`, el proceso DEBE abandonar la CPU de
  inmediato y pasar a Bloqueado durante `duracion` ticks; regresa a Listo en el instante
  (fin del tick de la solicitud + `duracion`).
- **FR-010**: Un proceso Bloqueado DEBE conservar su bloque de memoria; la E/S de distintos procesos
  ocurre en paralelo (sin cola de dispositivo).
- **FR-011**: Al completar su `tiempoRafaga`, el proceso DEBE pasar a Terminado y su memoria DEBE
  liberarse en ese mismo instante.
- **FR-012**: El sistema DEBE registrar cada transición en una bitácora con instante, proceso,
  estado origen, estado destino y motivo.

**Memoria RAM**

- **FR-013**: La RAM DEBE modelarse como un espacio contiguo de `memoriaTotal` MB con direcciones de
  0 a `memoriaTotal`−1, dividido en bloques asignados (base, límite = base + tamaño − 1, proceso) y
  huecos libres.
- **FR-014**: La asignación DEBE usar Best-Fit: el hueco libre más pequeño cuyo tamaño sea ≥ a la
  memoria requerida; en empate, el de menor base. El proceso ocupa el inicio del hueco y el
  sobrante queda como hueco.
- **FR-015**: Un proceso solo puede pasar de Nuevo a Listo si se le asigna memoria; si no, permanece
  en la cola de Espera de Memoria, ordenada por instante de llegada.
- **FR-015a**: Cada proceso en Espera de Memoria DEBE llevar un contador de adelantamientos, que se
  incrementa en 1 cada vez que se admite en memoria un proceso que llegó después que él (desde la
  cola de espera o recién llegado). Cuando el contador alcanza `maxAdelantamientos` (por defecto 5),
  ese proceso se vuelve **bloqueante**: ningún proceso posterior a él puede ser admitido hasta que él
  lo sea. Los procesos anteriores a él en la cola sí pueden seguir siendo admitidos. El contador y la
  condición de bloqueante DEBEN mostrarse en el dashboard y en la CLI.
- **FR-016**: Al liberar un bloque, el sistema DEBE fusionarlo con los huecos inmediatamente
  anterior y posterior, de modo que nunca existan dos huecos contiguos.
- **FR-017**: El sistema DEBE calcular en todo momento la memoria libre total, el mayor hueco
  contiguo y la fragmentación externa (memoria libre que no está en el mayor hueco).

**Dashboard web**

- **FR-018**: El dashboard DEBE estar disponible en el navegador en `http://localhost:8080` mientras
  el simulador está activo.
- **FR-019**: El dashboard DEBE mostrar el instante actual, el estado de la simulación (detenida,
  en curso, pausada, finalizada) y las colas Nuevos/Espera de RAM, Listos (en orden), En CPU,
  Bloqueados (con ticks de E/S restantes) y Terminados.
- **FR-020**: El dashboard DEBE mostrar un mapa de RAM proporcional a la capacidad, con cada bloque
  asignado identificado por proceso y con su base–límite, y cada hueco libre con su base–límite y
  tamaño.
- **FR-021**: El dashboard DEBE mostrar un diagrama de Gantt acumulativo que crezca tick a tick, con
  un tramo por turno de CPU y tramos ociosos diferenciados.
- **FR-022**: El dashboard DEBE ofrecer: botón Iniciar/Reanudar, botón Pausar, botón Reiniciar, control deslizante de
  velocidad en milisegundos por tick (rango 50–2000 ms, por defecto 500 ms) y campos para modificar
  quantum y capacidad de RAM, con las reglas de aplicación de la Historia 3.
- **FR-023**: La vista DEBE reflejar cada nuevo tick en menos de 1 segundo.
- **FR-024**: El dashboard DEBE mostrar las métricas: por proceso (llegada, finalización, retorno,
  espera) y globales (espera promedio, retorno promedio, utilización de CPU), actualizadas con los
  procesos terminados hasta el momento.

**CLI PowerShell**

- **FR-025**: El script `gestor.ps1` DEBE aceptar un identificador de proceso como argumento,
  consultar al simulador en ejecución y mostrar: estado, ticks consumidos, ticks restantes, bloque
  de RAM (base–límite y tamaño, o "sin asignar") y tiempo de espera acumulado (según FR-028).
- **FR-026**: El script DEBE mostrar mensajes de error claros en español y terminar con código
  distinto de cero cuando el proceso no existe, ya terminó, el simulador no responde o falta el
  argumento; si el proceso aún no llegó, lo informa.

**Métricas**

- **FR-027**: Tiempo de retorno = instante de finalización − `tiempoLlegada`.
- **FR-028**: Tiempo de espera = ticks transcurridos en Listo o en Espera de Memoria
  (equivalente a retorno − `tiempoRafaga` − tiempo total en E/S).
- **FR-029**: Utilización de CPU = ticks con la CPU ocupada / ticks totales hasta la finalización del
  último proceso × 100, expresada con un decimal.
- **FR-030**: Promedios de espera y retorno = media aritmética sobre los procesos terminados,
  expresada con dos decimales.

**General**

- **FR-031**: Todos los textos visibles (dashboard, consola, errores) DEBEN estar en español.
- **FR-032**: Dado el mismo archivo y la misma configuración, la simulación DEBE producir siempre la
  misma secuencia de eventos y métricas, independientemente de la velocidad o de las pausas.

### Key Entities

- **Configuración del escenario**: archivo de entrada con parámetros globales (`memoriaTotal`,
  `quantum`, `maxAdelantamientos`) y la lista de procesos.

  ```json
  {
    "configuracion": { "memoriaTotal": 1024, "quantum": 1, "maxAdelantamientos": 5 },
    "procesos": [
      {
        "id": "P1",
        "tiempoLlegada": 0,
        "tiempoRafaga": 5,
        "memoria": 200,
        "prioridad": 2,
        "bloqueosES": [ { "enQuantum": 2, "duracion": 3 } ]
      },
      { "id": "P2", "tiempoLlegada": 1, "tiempoRafaga": 3, "memoria": 120 }
    ]
  }
  ```

- **Proceso / PCB**: id, llegada, ráfaga total, ticks consumidos, ticks restantes, memoria
  requerida, prioridad, estado, eventos de E/S pendientes, bloque de memoria asignado, ticks de
  espera acumulados, ticks de E/S acumulados, contador de adelantamientos en Espera de Memoria,
  instante de finalización.
- **Bloque de memoria**: base, límite, tamaño y proceso propietario (o libre).
- **Mapa de memoria**: capacidad total y lista ordenada por dirección de bloques asignados y huecos.
- **Evento de bitácora**: instante, proceso, estado origen, estado destino, motivo.
- **Tramo de Gantt**: proceso (u ocioso), instante de inicio, instante de fin.
- **Estado de la simulación**: instante actual, modo (detenida, en curso, pausada, finalizada),
  quantum vigente, velocidad, colas, mapa de memoria, Gantt y métricas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los escenarios de referencia resueltos a mano (como mínimo los de las
  Historias 1 y 2) producen exactamente el Gantt, los instantes de finalización y las métricas
  esperadas.
- **SC-002**: El 100 % de los archivos de configuración inválidos de la batería de pruebas se
  rechazan antes de iniciar, con un mensaje que nombra el campo y el motivo.
- **SC-003**: Un estudiante puede, sin ayuda, cargar un escenario de 5 procesos, ejecutarlo y leer
  sus métricas finales en menos de 3 minutos.
- **SC-004**: El dashboard refleja cada tick en menos de 1 segundo con escenarios de hasta 50
  procesos y velocidades de hasta 50 ms por tick, sin saltarse estados intermedios en la bitácora.
- **SC-005**: La consulta por consola de un proceso responde en menos de 2 segundos y coincide con
  el estado mostrado en el dashboard para ese mismo instante.
- **SC-006**: Pausar y reanudar en cualquier punto, o cambiar la velocidad, no altera en ningún caso
  el resultado final de la simulación.
- **SC-007**: En ningún instante existen dos huecos de memoria contiguos ni la suma de bloques y
  huecos difiere de la capacidad total.

## Assumptions

- Todas las unidades temporales del JSON se interpretan como ticks; "quantum" en la configuración
  global designa la duración del turno en ticks, y `enQuantum` cuenta ticks de CPU consumidos por
  el proceso.
- Valores por defecto: RAM 1024 MB, quantum 1 tick, límite de adelantamientos 5, velocidad 500 ms
  por tick.
- La capacidad de RAM solo puede cambiarse con la simulación en el instante 0 sin iniciar (al
  arrancar o tras Reiniciar); el botón Reiniciar no permite elegir otro archivo de escenario.
- Reiniciar vuelve a leer `config.json` desde disco (refleja sus cambios); si el archivo ya no es
  válido, se informa el error y se reinicia con el último escenario válido cargado.
- El cambio de contexto y la asignación de memoria no consumen tiempo.
- Solo existe una CPU y la E/S no tiene contención entre procesos.
- La política de memoria es Best-Fit; First-Fit y otros planificadores (FCFS, Prioridades, SJF)
  quedan fuera de esta funcionalidad, aunque la solución debe permitir añadirlos después.
- No hay compactación de memoria ni intercambio (swapping).
- La CLI se comunica con el simulador que corre en la misma máquina; no se requiere autenticación
  porque el servicio solo es accesible localmente.
- Un único usuario controla la simulación a la vez; varios observadores pueden ver el dashboard.
