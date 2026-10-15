package gestor.motor;

import gestor.modelo.EstadoProceso;
import java.util.ArrayList;
import java.util.List;

/** Historial de transiciones en el orden en que las registra el motor (FR-012). */
public final class Bitacora {
    /**
     * Crea una instancia lista para su primer uso, sin estado previo.
     */
    public Bitacora() {}

    private final List<EventoBitacora> eventos = new ArrayList<>();

    /**
     * Añade una transición al final del historial sin reordenar eventos simultáneos.
     *
     * @param instante instante simulado en ticks
     * @param idProceso identificador del proceso que cambia de estado
     * @param origen estado anterior a la transición
     * @param destino estado solicitado para la transición
     * @param motivo descripción de la causa en español
     */
    public void registrar(int instante, String idProceso, EstadoProceso origen, EstadoProceso destino, String motivo) {
        eventos.add(new EventoBitacora(instante, idProceso, origen, destino, motivo));
    }

    /**
     * Consulta una copia inmutable de la bitácora completa.
     *
     * @return una copia inmutable de la bitácora completa
     */
    public List<EventoBitacora> eventos() {
        return List.copyOf(eventos);
    }

    /**
     * Elimina todos los elementos conservando la instancia para su reutilización.
     */
    public void vaciar() {
        eventos.clear();
    }
}
