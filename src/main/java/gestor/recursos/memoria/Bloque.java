package gestor.recursos.memoria;

/** Partición de RAM con direcciones inclusivas; un identificador nulo representa un hueco. */
public record Bloque(int base, int tamano, String idProceso) {
    public Bloque {
        if (base < 0 || tamano < 1 || (long) base + tamano - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El bloque debe tener base no negativa, tamaño positivo y límite válido");
        }
    }

    public int limite() {
        return base + tamano - 1;
    }

    public boolean esHueco() {
        return idProceso == null;
    }
}
