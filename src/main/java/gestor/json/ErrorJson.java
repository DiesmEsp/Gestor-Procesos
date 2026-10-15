package gestor.json;

/** Error sintáctico con posición de origen basada en uno. */
public final class ErrorJson extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int linea;
    private final int columna;

    public ErrorJson(int linea, int columna, String motivo) {
        super("línea " + linea + ", columna " + columna + ": " + motivo);
        this.linea = linea;
        this.columna = columna;
    }

    public int linea() {
        return linea;
    }

    public int columna() {
        return columna;
    }
}
