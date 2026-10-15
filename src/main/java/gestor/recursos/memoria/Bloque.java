package gestor.recursos.memoria;

/**
 * Partición de RAM con direcciones inclusivas; un identificador nulo representa un hueco.
 *
 * @param base dirección inicial del bloque, incluida
 * @param tamano tamaño positivo del bloque en MB
 * @param idProceso identificador del proceso; nulo para un hueco libre
 */
public record Bloque(int base, int tamano, String idProceso) {
    /**
     * Valida la base no negativa y el tamaño positivo de la partición.
     *
     * @param base dirección inicial del bloque, incluida
     * @param tamano tamaño positivo del bloque en MB
     * @param idProceso identificador del proceso; nulo para un hueco libre
     */
    public Bloque {
        if (base < 0 || tamano < 1 || (long) base + tamano - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El bloque debe tener base no negativa, tamaño positivo y límite válido");
        }
    }

    /**
     * Consulta la dirección final incluida del bloque.
     *
     * @return la dirección final incluida del bloque
     */
    public int limite() {
        return base + tamano - 1;
    }

    /**
     * Indica si la partición representa memoria libre.
     *
     * @return verdadero si el identificador del proceso es nulo
     */
    public boolean esHueco() {
        return idProceso == null;
    }
}
