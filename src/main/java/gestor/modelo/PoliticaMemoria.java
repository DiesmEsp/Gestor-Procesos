package gestor.modelo;

import java.util.Optional;

/** Políticas de asignación seleccionables mediante su código exacto. */
public enum PoliticaMemoria {
    BEST_FIT("Best-Fit"),
    FIRST_FIT("First-Fit");

    private final String etiqueta;

    PoliticaMemoria(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static Optional<PoliticaMemoria> desdeCodigo(String codigo) {
        for (PoliticaMemoria politica : values()) {
            if (politica.name().equals(codigo)) {
                return Optional.of(politica);
            }
        }
        return Optional.empty();
    }

    public static String valoresValidos() {
        return "BEST_FIT, FIRST_FIT";
    }
}
