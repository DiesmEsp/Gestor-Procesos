package gestor.modelo;

import java.util.Optional;

/** Políticas de asignación seleccionables mediante su código exacto. */
public enum PoliticaMemoria {
    /** Elige el menor hueco suficiente; desempata por menor dirección base. */
    BEST_FIT("Best-Fit"),
    /** Elige el primer hueco suficiente en orden de dirección base. */
    FIRST_FIT("First-Fit");

    private final String etiqueta;

    PoliticaMemoria(String etiqueta) {
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
    public static Optional<PoliticaMemoria> desdeCodigo(String codigo) {
        for (PoliticaMemoria politica : values()) {
            if (politica.name().equals(codigo)) {
                return Optional.of(politica);
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
        return "BEST_FIT, FIRST_FIT";
    }
}
