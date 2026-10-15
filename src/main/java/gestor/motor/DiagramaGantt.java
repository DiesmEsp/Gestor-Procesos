package gestor.motor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compacta ticks contiguos conservando los límites de cada turno de CPU. */
public final class DiagramaGantt {
    private final List<TramoGantt> tramos = new ArrayList<>();

    public void registrarTick(int instante, String idProceso, boolean nuevoTurno) {
        if (instante < 0 || instante == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El instante debe permitir representar un tick no negativo");
        }
        if (!tramos.isEmpty()) {
            int indiceUltimo = tramos.size() - 1;
            TramoGantt ultimo = tramos.get(indiceUltimo);
            if (instante < ultimo.fin()) {
                throw new IllegalArgumentException("Los ticks del Gantt deben registrarse en orden y sin solapamientos");
            }
            // Los ticks ociosos consecutivos se agrupan aunque se indique un nuevo turno.
            if (ultimo.fin() == instante && Objects.equals(ultimo.idProceso(), idProceso)
                    && (!nuevoTurno || idProceso == null)) {
                tramos.set(indiceUltimo, new TramoGantt(idProceso, ultimo.inicio(), instante + 1));
                return;
            }
        }
        tramos.add(new TramoGantt(idProceso, instante, instante + 1));
    }

    public List<TramoGantt> tramos() {
        return List.copyOf(tramos);
    }
}
