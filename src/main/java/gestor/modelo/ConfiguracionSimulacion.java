package gestor.modelo;

import java.util.Objects;

/**
 * Parámetros inmutables de la simulación; los tiempos se expresan en ticks.
 *
 * @param memoriaTotal capacidad total de RAM en MB, mayor o igual a uno
 * @param quantum duración máxima del turno en ticks, mayor o igual a uno
 * @param maxAdelantamientos límite positivo de admisiones posteriores antes de proteger al proceso
 * @param politicaMemoria política de asignación seleccionada
 * @param planificador estrategia de planificación de CPU
 */
public record ConfiguracionSimulacion(int memoriaTotal, int quantum, int maxAdelantamientos,
        PoliticaMemoria politicaMemoria, AlgoritmoPlanificacion planificador) {
    /** Capacidad predeterminada de RAM en MB. */
    public static final int MEMORIA_POR_DEFECTO = 1024;
    /** Duración predeterminada de un turno en ticks. */
    public static final int QUANTUM_POR_DEFECTO = 1;
    /** Límite predeterminado de adelantamientos en espera de RAM. */
    public static final int MAX_ADELANTAMIENTOS_POR_DEFECTO = 5;
    /** Estrategia predeterminada de elección de huecos. */
    public static final PoliticaMemoria POLITICA_POR_DEFECTO = PoliticaMemoria.BEST_FIT;
    /** Estrategia predeterminada de planificación de CPU. */
    public static final AlgoritmoPlanificacion PLANIFICADOR_POR_DEFECTO = AlgoritmoPlanificacion.ROUND_ROBIN;

    /**
     * Valida las capacidades positivas y las estrategias no nulas.
     *
     * @param memoriaTotal capacidad total de RAM en MB, mayor o igual a uno
     * @param quantum duración máxima del turno en ticks, mayor o igual a uno
     * @param maxAdelantamientos límite positivo de admisiones posteriores antes de proteger al proceso
     * @param politicaMemoria política de asignación seleccionada
     * @param planificador estrategia de planificación de CPU
     */
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

    /**
     * Construye los parámetros predeterminados del contrato de configuración.
     *
     * @return la configuración predeterminada
     */
    public static ConfiguracionSimulacion porDefecto() {
        return new ConfiguracionSimulacion(MEMORIA_POR_DEFECTO, QUANTUM_POR_DEFECTO,
                MAX_ADELANTAMIENTOS_POR_DEFECTO, POLITICA_POR_DEFECTO, PLANIFICADOR_POR_DEFECTO);
    }

    /**
     * Crea una configuración nueva cambiando únicamente el quantum.
     *
     * @param quantum duración máxima del turno en ticks, mayor o igual a uno
     * @return la configuración con el nuevo quantum
     */
    public ConfiguracionSimulacion conQuantum(int quantum) {
        return new ConfiguracionSimulacion(memoriaTotal, quantum, maxAdelantamientos, politicaMemoria, planificador);
    }

    /**
     * Crea una configuración nueva cambiando únicamente la capacidad de RAM.
     *
     * @param memoriaTotal capacidad total de RAM en MB, mayor o igual a uno
     * @return la configuración con la nueva capacidad
     */
    public ConfiguracionSimulacion conMemoriaTotal(int memoriaTotal) {
        return new ConfiguracionSimulacion(memoriaTotal, quantum, maxAdelantamientos, politicaMemoria, planificador);
    }

    /**
     * Crea una configuración nueva cambiando únicamente la política de asignación.
     *
     * @param politicaMemoria política de asignación seleccionada
     * @return la configuración con la nueva política
     */
    public ConfiguracionSimulacion conPoliticaMemoria(PoliticaMemoria politicaMemoria) {
        return new ConfiguracionSimulacion(memoriaTotal, quantum, maxAdelantamientos, politicaMemoria, planificador);
    }
}
