package gestor.motor;

/** Intervalo [inicio, fin) de CPU; un identificador nulo representa CPU ociosa. */
public record TramoGantt(String idProceso, int inicio, int fin) {
}
