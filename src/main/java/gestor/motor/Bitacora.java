package gestor.motor;

import gestor.modelo.EstadoProceso;
import java.util.ArrayList;
import java.util.List;

/** Historial de transiciones en el orden en que las registra el motor (FR-012). */
public final class Bitacora {
    private final List<EventoBitacora> eventos = new ArrayList<>();

    public void registrar(int instante, String idProceso, EstadoProceso origen, EstadoProceso destino, String motivo) {
        eventos.add(new EventoBitacora(instante, idProceso, origen, destino, motivo));
    }

    public List<EventoBitacora> eventos() {
        return List.copyOf(eventos);
    }

    public void vaciar() {
        eventos.clear();
    }
}
