package gestor.modelo;

/** Estados del PCB y transiciones válidas del ciclo de vida (FR-007). */
public enum EstadoProceso {
    /** El proceso está definido pero todavía no ha llegado. */
    PENDIENTE_LLEGADA("Pendiente de llegada"),
    /** Llegó y espera una partición contigua de RAM. */
    NUEVO("Nuevo / Espera de memoria"),
    /** Conserva RAM y espera su turno en la cola de CPU. */
    LISTO("Listo"),
    /** Consume CPU en el turno actual. */
    EJECUCION("En Ejecución"),
    /** Realiza E/S simulada sin liberar la memoria. */
    BLOQUEADO("Bloqueado"),
    /** Completó su ráfaga y liberó la RAM. */
    TERMINADO("Terminado");

    private final String etiqueta;

    EstadoProceso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * Comprueba si el ciclo de vida permite pasar al estado indicado.
     *
     * @param destino estado solicitado para la transición
     * @return verdadero si la transición es válida
     */
    public boolean puedeTransicionarA(EstadoProceso destino) {
        return switch (this) {
            case PENDIENTE_LLEGADA -> destino == NUEVO;
            case NUEVO -> destino == LISTO;
            case LISTO -> destino == EJECUCION;
            case EJECUCION -> destino == LISTO || destino == BLOQUEADO || destino == TERMINADO;
            case BLOQUEADO -> destino == LISTO;
            case TERMINADO -> false;
        };
    }

    /**
     * Consulta la etiqueta en español para las interfaces.
     *
     * @return la etiqueta en español para las interfaces
     */
    public String etiqueta() {
        return etiqueta;
    }
}
