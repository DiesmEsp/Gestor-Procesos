package gestor.modelo;

import java.util.List;
import java.util.Objects;

/** Configuración y definiciones de procesos, sin referencias a listas modificables. */
public record Escenario(ConfiguracionSimulacion configuracion, List<DefinicionProceso> procesos) {
    public Escenario {
        Objects.requireNonNull(configuracion, "La configuración no puede ser nula");
        procesos = List.copyOf(procesos);
    }
}
