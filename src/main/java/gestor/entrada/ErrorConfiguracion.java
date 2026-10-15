package gestor.entrada;

import java.util.List;

/** Diagnósticos acumulados de un escenario, utilizables desde un proveedor sin excepciones comprobadas. */
public final class ErrorConfiguracion extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final List<String> errores;

    public ErrorConfiguracion(List<String> errores) {
        super(String.join(System.lineSeparator(), errores));
        this.errores = List.copyOf(errores);
    }

    public List<String> errores() {
        return errores;
    }
}
