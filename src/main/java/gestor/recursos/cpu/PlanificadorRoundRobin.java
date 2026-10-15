package gestor.recursos.cpu;

import gestor.modelo.Proceso;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** Cola FIFO de Round Robin con expropiación al consumir el quantum (FR-008). */
public final class PlanificadorRoundRobin implements Planificador {
    private final ArrayDeque<Proceso> listos = new ArrayDeque<>();

    @Override
    public void encolar(Proceso proceso) {
        listos.addLast(Objects.requireNonNull(proceso, "El proceso a encolar no puede ser nulo"));
    }

    @Override
    public Proceso siguiente() {
        return listos.pollFirst();
    }

    @Override
    public boolean debeExpropiar(Proceso enEjecucion, int quantum) {
        if (quantum < 1) {
            throw new IllegalArgumentException("El quantum debe ser mayor o igual a 1 tick");
        }
        Objects.requireNonNull(enEjecucion, "El proceso en ejecución no puede ser nulo");
        return enEjecucion.ticksQuantumUsados() >= quantum;
    }

    @Override
    public List<Proceso> cola() {
        return List.copyOf(listos);
    }

    @Override
    public void vaciar() {
        listos.clear();
    }

    @Override
    public String nombre() {
        return "Round Robin";
    }
}
