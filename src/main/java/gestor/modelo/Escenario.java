package gestor.modelo;

import java.util.List;
import java.util.Objects;

/**
 * Configuración y definiciones de procesos, sin referencias a listas modificables.
 *
 * @param configuracion parámetros vigentes de la simulación
 * @param procesos procesos en orden de archivo
 */
public record Escenario(ConfiguracionSimulacion configuracion, List<DefinicionProceso> procesos) {
    /**
     * Conserva una lista inmutable de procesos y exige configuración no nula.
     *
     * @param configuracion parámetros vigentes de la simulación
     * @param procesos procesos en orden de archivo
     */
    public Escenario {
        Objects.requireNonNull(configuracion, "La configuración no puede ser nula");
        procesos = List.copyOf(procesos);
    }
}
