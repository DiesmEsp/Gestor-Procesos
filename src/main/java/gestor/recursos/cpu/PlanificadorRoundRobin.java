package gestor.recursos.cpu;

import gestor.modelo.Proceso;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** Cola FIFO de Round Robin con expropiación al consumir el quantum (FR-008). */
public final class PlanificadorRoundRobin implements Planificador {
    /**
     * Crea una instancia lista para su primer uso, sin estado previo.
     */
    public PlanificadorRoundRobin() {}

    private final ArrayDeque<Proceso> listos = new ArrayDeque<>();

    /**
     * Incorpora un proceso al final de la cola de Listos.
     *
     * @param proceso proceso consultado o incorporado a la cola
     */
    @Override
    public void encolar(Proceso proceso) {
        listos.addLast(Objects.requireNonNull(proceso, "El proceso a encolar no puede ser nulo"));
    }

    /**
     * Extrae el primer listo; devuelve nulo si la cola está vacía.
     *
     * @return el primer listo, o nulo con cola vacía
     */
    @Override
    public Proceso siguiente() {
        return listos.pollFirst();
    }

    /**
     * Comprueba si el proceso consumió el quantum del turno.
     *
     * @param enEjecucion PCB que ocupa la CPU
     * @param quantum duración máxima del turno en ticks, mayor o igual a uno
     * @return verdadero si se agotó el quantum del turno
     */
    @Override
    public boolean debeExpropiar(Proceso enEjecucion, int quantum) {
        if (quantum < 1) {
            throw new IllegalArgumentException("El quantum debe ser mayor o igual a 1 tick");
        }
        Objects.requireNonNull(enEjecucion, "El proceso en ejecución no puede ser nulo");
        return enEjecucion.ticksQuantumUsados() >= quantum;
    }

    /**
     * Consulta una copia inmutable de los procesos listos en orden FIFO.
     *
     * @return una copia inmutable de los procesos listos en orden FIFO
     */
    @Override
    public List<Proceso> cola() {
        return List.copyOf(listos);
    }

    /**
     * Elimina todos los elementos conservando la instancia para su reutilización.
     */
    @Override
    public void vaciar() {
        listos.clear();
    }

    /**
     * Devuelve el nombre de la estrategia para las interfaces.
     *
     * @return la etiqueta de la estrategia
     */
    @Override
    public String nombre() {
        return "Round Robin";
    }
}
