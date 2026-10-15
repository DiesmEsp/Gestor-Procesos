package gestor.motor;

import gestor.modelo.EstadoProceso;

/** Transición registrada en un instante de simulación con su motivo en español. */
public record EventoBitacora(int instante, String idProceso, EstadoProceso origen,
        EstadoProceso destino, String motivo) {
}
