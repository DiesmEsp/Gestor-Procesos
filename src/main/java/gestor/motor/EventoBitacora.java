package gestor.motor;

import gestor.modelo.EstadoProceso;

/**
 * Transición registrada en un instante de simulación con su motivo en español.
 *
 * @param instante instante simulado en ticks
 * @param idProceso identificador del proceso que cambia de estado
 * @param origen estado anterior a la transición
 * @param destino estado solicitado para la transición
 * @param motivo descripción de la causa en español
 */
public record EventoBitacora(int instante, String idProceso, EstadoProceso origen,
        EstadoProceso destino, String motivo) {
}
