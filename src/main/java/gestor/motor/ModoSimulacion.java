package gestor.motor;

/** Estado del reloj y de los controles de la simulación. */
public enum ModoSimulacion {
    /** Instante cero, aún sin iniciar; permite editar RAM y política. */
    DETENIDA,
    /** El reloj puede ejecutar ticks completos. */
    EN_CURSO,
    /** El reloj conserva el estado sin avanzar hasta reanudar. */
    PAUSADA,
    /** Todos los procesos terminaron; solo Reiniciar permite otra ejecución. */
    FINALIZADA
}
