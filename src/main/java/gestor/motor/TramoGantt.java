package gestor.motor;

/**
 * Intervalo [inicio, fin) de CPU; un identificador nulo representa CPU ociosa.
 *
 * @param idProceso identificador del proceso; nulo para un tramo ocioso
 * @param inicio instante inicial incluido
 * @param fin instante final excluido
 */
public record TramoGantt(String idProceso, int inicio, int fin) {
}
