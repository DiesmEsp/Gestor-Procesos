package gestor.entrada;

import java.util.List;

/** Diagnósticos acumulados de un escenario, utilizables desde un proveedor sin excepciones comprobadas. */
public final class ErrorConfiguracion extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * Diagnósticos completos conservados en el orden de validación.
     * @serial Lista inmutable de los errores de configuración.
     */
    private final List<String> errores;

    /**
     * Conserva todos los errores de validación y los une en el mensaje.
     *
     * @param errores diagnósticos completos en español
     */
    public ErrorConfiguracion(List<String> errores) {
        super(String.join(System.lineSeparator(), errores));
        this.errores = List.copyOf(errores);
    }

    /**
     * Consulta los diagnósticos en una lista inmutable.
     *
     * @return los diagnósticos en una lista inmutable
     */
    public List<String> errores() {
        return errores;
    }
}
