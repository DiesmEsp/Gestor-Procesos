package gestor.modelo;

import java.util.List;

/**
 * Datos inmutables de un proceso, producidos por la capa de entrada.
 *
 * @param id identificador único del proceso
 * @param tiempoLlegada instante de llegada en ticks
 * @param tiempoRafaga demanda total de CPU en ticks
 * @param memoria memoria requerida en MB
 * @param prioridad prioridad opcional; nula si no se especificó
 * @param bloqueosES eventos de E/S ordenados por consumo de CPU
 * @param ordenArchivo posición original para desempatar llegadas simultáneas
 */
public record DefinicionProceso(String id, int tiempoLlegada, int tiempoRafaga, int memoria,
        Integer prioridad, List<EventoES> bloqueosES, int ordenArchivo) {
    /**
     * Copia los eventos de E/S para impedir modificaciones externas.
     *
     * @param id identificador único del proceso
     * @param tiempoLlegada instante de llegada en ticks
     * @param tiempoRafaga demanda total de CPU en ticks
     * @param memoria memoria requerida en MB
     * @param prioridad prioridad opcional; nula si no se especificó
     * @param bloqueosES eventos de E/S ordenados por consumo de CPU
     * @param ordenArchivo posición original para desempatar llegadas simultáneas
     */
    public DefinicionProceso {
        bloqueosES = bloqueosES == null ? List.of() : List.copyOf(bloqueosES);
    }
}
