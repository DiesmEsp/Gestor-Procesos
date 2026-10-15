package gestor.modelo;

import java.util.Objects;

/** Parámetros inmutables de la simulación; los tiempos se expresan en ticks. */
public record ConfiguracionSimulacion(int memoriaTotal, int quantum, int maxAdelantamientos,
        PoliticaMemoria politicaMemoria, AlgoritmoPlanificacion planificador) {
    public static final int MEMORIA_POR_DEFECTO = 1024;
    public static final int QUANTUM_POR_DEFECTO = 1;
    public static final int MAX_ADELANTAMIENTOS_POR_DEFECTO = 5;
    public static final PoliticaMemoria POLITICA_POR_DEFECTO = PoliticaMemoria.BEST_FIT;
    public static final AlgoritmoPlanificacion PLANIFICADOR_POR_DEFECTO = AlgoritmoPlanificacion.ROUND_ROBIN;

    public ConfiguracionSimulacion {
        if (memoriaTotal < 1) {
            throw new IllegalArgumentException("La memoria total debe ser mayor o igual a 1 MB");
        }
        if (quantum < 1) {
            throw new IllegalArgumentException("El quantum debe ser mayor o igual a 1 tick");
        }
        if (maxAdelantamientos < 1) {
            throw new IllegalArgumentException("El máximo de adelantamientos debe ser mayor o igual a 1");
        }
        Objects.requireNonNull(politicaMemoria, "La política de memoria no puede ser nula");
        Objects.requireNonNull(planificador, "El planificador no puede ser nulo");
    }

    public static ConfiguracionSimulacion porDefecto() {
        return new ConfiguracionSimulacion(MEMORIA_POR_DEFECTO, QUANTUM_POR_DEFECTO,
                MAX_ADELANTAMIENTOS_POR_DEFECTO, POLITICA_POR_DEFECTO, PLANIFICADOR_POR_DEFECTO);
    }

    public ConfiguracionSimulacion conQuantum(int quantum) {
        return new ConfiguracionSimulacion(memoriaTotal, quantum, maxAdelantamientos, politicaMemoria, planificador);
    }

    public ConfiguracionSimulacion conMemoriaTotal(int memoriaTotal) {
        return new ConfiguracionSimulacion(memoriaTotal, quantum, maxAdelantamientos, politicaMemoria, planificador);
    }

    public ConfiguracionSimulacion conPoliticaMemoria(PoliticaMemoria politicaMemoria) {
        return new ConfiguracionSimulacion(memoriaTotal, quantum, maxAdelantamientos, politicaMemoria, planificador);
    }
}
