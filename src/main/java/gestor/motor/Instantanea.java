package gestor.motor;

import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.EstadoProceso;
import java.util.List;

/**
 * Captura inmutable de un instante completo; nunca contiene referencias a PCB mutables.
 *
 * @param instante instante simulado en ticks
 * @param modo modo del controlador; nulo en la captura neutra del motor
 * @param configuracion parámetros vigentes de la simulación
 * @param velocidadMs intervalo entre ticks en milisegundos; cero en la captura neutra
 * @param colas captura inmutable de las colas y la CPU
 * @param procesos procesos en orden de archivo
 * @param memoria captura inmutable del mapa de RAM
 * @param gantt tramos acumulados por turno de CPU
 * @param bitacora eventos acumulados en orden de registro
 * @param metricas métricas calculadas para este instante
 * @param aviso mensaje informativo; nulo si no hay aviso
 */
public record Instantanea(int instante, ModoSimulacion modo, ConfiguracionSimulacion configuracion,
        int velocidadMs, Colas colas, List<ResumenProceso> procesos, EstadoMemoria memoria,
        List<TramoGantt> gantt, List<EventoBitacora> bitacora, Metricas metricas, String aviso) {
    /**
     * Copia las listas para que la captura permanezca estable entre ticks.
     *
     * @param instante instante simulado en ticks
     * @param modo modo del controlador; nulo en la captura neutra del motor
     * @param configuracion parámetros vigentes de la simulación
     * @param velocidadMs intervalo entre ticks en milisegundos; cero en la captura neutra
     * @param colas captura inmutable de las colas y la CPU
     * @param procesos procesos en orden de archivo
     * @param memoria captura inmutable del mapa de RAM
     * @param gantt tramos acumulados por turno de CPU
     * @param bitacora eventos acumulados en orden de registro
     * @param metricas métricas calculadas para este instante
     * @param aviso mensaje informativo; nulo si no hay aviso
     */
    public Instantanea {
        procesos = List.copyOf(procesos);
        gantt = List.copyOf(gantt);
        bitacora = List.copyOf(bitacora);
    }

    /**
     * Crea una captura con el contexto del controlador sin modificar la original.
     *
     * @param modo modo del controlador; nulo en la captura neutra del motor
     * @param velocidadMs intervalo entre ticks en milisegundos; cero en la captura neutra
     * @param aviso mensaje informativo; nulo si no hay aviso
     * @return una nueva captura con el contexto indicado
     */
    public Instantanea con(ModoSimulacion modo, int velocidadMs, String aviso) {
        return new Instantanea(instante, modo, configuracion, velocidadMs, colas, procesos, memoria,
                gantt, bitacora, metricas, aviso);
    }

    /**
     * Proceso que espera RAM, con contador y protección anti-inanición.
     *
     * @param id identificador único del proceso
     * @param memoria memoria requerida en MB
     * @param adelantamientos admisiones de procesos posteriores mientras se espera RAM
     * @param bloqueante si el proceso en Nuevo alcanzó el límite de adelantamientos
     */
    public record ProcesoEnEspera(String id, int memoria, int adelantamientos, boolean bloqueante) {}
    /**
     * Identificador de la CPU y ticks consumidos en su turno.
     *
     * @param id identificador único del proceso
     * @param ticksQuantumUsados ticks consumidos en el turno actual
     */
    public record ProcesoEnEjecucion(String id, int ticksQuantumUsados) {}
    /**
     * Proceso bloqueado con su duración restante de E/S.
     *
     * @param id identificador único del proceso
     * @param ticksRestantesES ticks pendientes de E/S, sin valores negativos
     */
    public record ProcesoBloqueado(String id, int ticksRestantesES) {}
    /**
     * Partición inmutable de RAM; identificador nulo representa un hueco.
     *
     * @param base dirección inicial del bloque, incluida
     * @param limite dirección final del bloque, incluida
     * @param tamano tamaño positivo del bloque en MB
     * @param idProceso identificador del proceso; nulo para un hueco libre
     */
    public record BloqueMemoria(int base, int limite, int tamano, String idProceso) {}
    /**
     * Datos públicos de un PCB, sin referencias mutables al motor.
     *
     * @param id identificador único del proceso
     * @param estado estado visible del proceso
     * @param tiempoLlegada instante de llegada en ticks
     * @param tiempoRafaga demanda total de CPU en ticks
     * @param ticksConsumidos ticks de CPU acumulados
     * @param ticksRestantes ticks de CPU todavía necesarios
     * @param memoria memoria requerida en MB
     * @param prioridad prioridad opcional; nula si no se especificó
     * @param bloque bloque asignado; nulo sin asignación
     * @param tiempoEspera ticks acumulados en Listo y en espera de RAM
     * @param ticksES ticks acumulados en Bloqueado
     * @param adelantamientos admisiones de procesos posteriores mientras se espera RAM
     * @param bloqueante si el proceso en Nuevo alcanzó el límite de adelantamientos
     * @param ticksRestantesES ticks pendientes de E/S; nulo fuera de Bloqueado en el resumen
     * @param instanteFinalizacion instante de finalización; nulo mientras no termine
     * @param tiempoRetorno finalización menos llegada; nulo mientras no termine
     */
    public record ResumenProceso(String id, EstadoProceso estado, int tiempoLlegada, int tiempoRafaga,
            int ticksConsumidos, int ticksRestantes, int memoria, Integer prioridad, BloqueMemoria bloque,
            int tiempoEspera, int ticksES, int adelantamientos, boolean bloqueante, Integer ticksRestantesES,
            Integer instanteFinalizacion, Integer tiempoRetorno) {}

    /**
     * Mapa contiguo de RAM con sus indicadores de fragmentación.
     *
     * @param total capacidad total de RAM en MB
     * @param libre memoria libre total en MB
     * @param mayorHueco mayor hueco contiguo en MB
     * @param fragmentacionExterna memoria libre menos el mayor hueco, en MB
     * @param bloques particiones inmutables ordenadas por dirección
     */
    public record EstadoMemoria(int total, int libre, int mayorHueco, int fragmentacionExterna,
            List<BloqueMemoria> bloques) {
        /**
         * Copia las particiones para impedir modificaciones externas.
         *
         * @param total capacidad total de RAM en MB
         * @param libre memoria libre total en MB
         * @param mayorHueco mayor hueco contiguo en MB
         * @param fragmentacionExterna memoria libre menos el mayor hueco, en MB
         * @param bloques particiones inmutables ordenadas por dirección
         */
        public EstadoMemoria { bloques = List.copyOf(bloques); }
    }

    /**
     * Captura inmutable de la admisión, planificación, CPU, bloqueos y finalizados.
     *
     * @param nuevos procesos en espera de RAM por orden de admisión
     * @param listos identificadores en orden FIFO
     * @param ejecucion proceso que ocupa la CPU; nulo si está libre
     * @param bloqueados procesos bloqueados en orden de entrada a E/S
     * @param terminados identificadores por orden de finalización
     */
    public record Colas(List<ProcesoEnEspera> nuevos, List<String> listos, ProcesoEnEjecucion ejecucion,
            List<ProcesoBloqueado> bloqueados, List<String> terminados) {
        /**
         * Copia todas las colas conservando su orden de simulación.
         *
         * @param nuevos procesos en espera de RAM por orden de admisión
         * @param listos identificadores en orden FIFO
         * @param ejecucion proceso que ocupa la CPU; nulo si está libre
         * @param bloqueados procesos bloqueados en orden de entrada a E/S
         * @param terminados identificadores por orden de finalización
         */
        public Colas {
            nuevos = List.copyOf(nuevos);
            listos = List.copyOf(listos);
            bloqueados = List.copyOf(bloqueados);
            terminados = List.copyOf(terminados);
        }
    }
}
