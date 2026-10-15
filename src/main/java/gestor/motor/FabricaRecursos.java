package gestor.motor;

import gestor.modelo.AlgoritmoPlanificacion;
import gestor.modelo.PoliticaMemoria;
import gestor.recursos.cpu.Planificador;
import gestor.recursos.cpu.PlanificadorRoundRobin;
import gestor.recursos.memoria.MejorAjuste;
import gestor.recursos.memoria.PoliticaAsignacion;
import gestor.recursos.memoria.PrimerAjuste;
import java.util.Objects;

/** Compone los recursos a partir de los códigos admitidos en la configuración. */
public final class FabricaRecursos {
    private FabricaRecursos() {
    }

    public static PoliticaAsignacion crearPolitica(PoliticaMemoria politica) {
        Objects.requireNonNull(politica, "La política de memoria no puede ser nula");
        return switch (politica) {
            case BEST_FIT -> new MejorAjuste();
            case FIRST_FIT -> new PrimerAjuste();
        };
    }

    public static Planificador crearPlanificador(AlgoritmoPlanificacion algoritmo) {
        Objects.requireNonNull(algoritmo, "El algoritmo de planificación no puede ser nulo");
        return switch (algoritmo) {
            case ROUND_ROBIN -> new PlanificadorRoundRobin();
        };
    }
}
