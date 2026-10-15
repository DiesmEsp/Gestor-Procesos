package gestor.json;

/** Error sintáctico con posición de origen basada en uno. */
public final class ErrorJson extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * Línea del error basada en uno.
     * @serial Posición de línea del diagnóstico.
     */
    private final int linea;
    /**
     * Columna del error basada en uno.
     * @serial Posición de columna del diagnóstico.
     */
    private final int columna;

    /**
     * Construye un diagnóstico sintáctico con línea, columna y causa.
     *
     * @param linea línea de origen basada en uno
     * @param columna columna de origen basada en uno
     * @param motivo descripción de la causa en español
     */
    public ErrorJson(int linea, int columna, String motivo) {
        super("línea " + linea + ", columna " + columna + ": " + motivo);
        this.linea = linea;
        this.columna = columna;
    }

    /**
     * Consulta la línea del error basada en uno.
     *
     * @return la línea del error basada en uno
     */
    public int linea() {
        return linea;
    }

    /**
     * Consulta la columna del error basada en uno.
     *
     * @return la columna del error basada en uno
     */
    public int columna() {
        return columna;
    }
}
