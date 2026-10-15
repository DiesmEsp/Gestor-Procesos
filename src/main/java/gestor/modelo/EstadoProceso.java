package gestor.modelo;

/** Estados del PCB y transiciones válidas del ciclo de vida (FR-007). */
public enum EstadoProceso {
    PENDIENTE_LLEGADA("Pendiente de llegada"),
    NUEVO("Nuevo / Espera de memoria"),
    LISTO("Listo"),
    EJECUCION("En Ejecución"),
    BLOQUEADO("Bloqueado"),
    TERMINADO("Terminado");

    private final String etiqueta;

    EstadoProceso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

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

    public String etiqueta() {
        return etiqueta;
    }
}
