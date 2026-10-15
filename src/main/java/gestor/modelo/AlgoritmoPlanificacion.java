package gestor.modelo;

import java.util.Optional;

/** Algoritmos de CPU disponibles en la configuración. */
public enum AlgoritmoPlanificacion {
    /** Turnos FIFO con expropiación al consumir el quantum. */
    ROUND_ROBIN("Round Robin");

    private final String etiqueta;

    AlgoritmoPlanificacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * Consulta la etiqueta en español para las interfaces.
     *
     * @return la etiqueta en español para las interfaces
     */
    public String etiqueta() {
        return etiqueta;
    }

    /**
     * Busca una opción por su código exacto, sin normalizar mayúsculas.
     *
     * @param codigo código exacto y sensible a mayúsculas del contrato
     * @return la opción reconocida, o vacío si el código no existe
     */
    public static Optional<AlgoritmoPlanificacion> desdeCodigo(String codigo) {
        for (AlgoritmoPlanificacion algoritmo : values()) {
            if (algoritmo.name().equals(codigo)) {
                return Optional.of(algoritmo);
            }
        }
        return Optional.empty();
    }

    /**
     * Consulta los códigos admitidos separados por comas.
     *
     * @return los códigos admitidos separados por comas
     */
    public static String valoresValidos() {
        return "ROUND_ROBIN";
    }
}
