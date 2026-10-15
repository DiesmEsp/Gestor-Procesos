package gestor.modelo;

import java.util.List;

/** Datos inmutables de un proceso, producidos por la capa de entrada. */
public record DefinicionProceso(String id, int tiempoLlegada, int tiempoRafaga, int memoria,
        Integer prioridad, List<EventoES> bloqueosES, int ordenArchivo) {
    public DefinicionProceso {
        bloqueosES = bloqueosES == null ? List.of() : List.copyOf(bloqueosES);
    }
}
