package gestor.modelo;

import java.util.Optional;

/** Algoritmos de CPU disponibles en la configuración. */
public enum AlgoritmoPlanificacion {
    ROUND_ROBIN("Round Robin");

    private final String etiqueta;

    AlgoritmoPlanificacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static Optional<AlgoritmoPlanificacion> desdeCodigo(String codigo) {
        for (AlgoritmoPlanificacion algoritmo : values()) {
            if (algoritmo.name().equals(codigo)) {
                return Optional.of(algoritmo);
            }
        }
        return Optional.empty();
    }

    public static String valoresValidos() {
        return "ROUND_ROBIN";
    }
}
