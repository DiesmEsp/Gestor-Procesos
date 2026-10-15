package gestor.recursos.cpu;

import gestor.modelo.Proceso;
import java.util.List;

/** Estrategia de selección y expropiación de los procesos listos. */
public interface Planificador {
    /**
     * Incorpora un proceso al final de la cola de Listos.
     *
     * @param proceso proceso consultado o incorporado a la cola
     */
    void encolar(Proceso proceso);

    /**
     * Extrae el primer listo; devuelve nulo si la cola está vacía.
     *
     * @return el primer listo, o nulo con cola vacía
     */
    Proceso siguiente();

    /**
     * Comprueba si el proceso consumió el quantum del turno.
     *
     * @param enEjecucion PCB que ocupa la CPU
     * @param quantum duración máxima del turno en ticks, mayor o igual a uno
     * @return verdadero si se agotó el quantum del turno
     */
    boolean debeExpropiar(Proceso enEjecucion, int quantum);

    /**
     * Consulta una copia inmutable de los procesos listos en orden FIFO.
     *
     * @return una copia inmutable de los procesos listos en orden FIFO
     */
    List<Proceso> cola();

    /**
     * Elimina todos los elementos conservando la instancia para su reutilización.
     */
    void vaciar();

    /**
     * Devuelve el nombre de la estrategia para las interfaces.
     *
     * @return la etiqueta de la estrategia
     */
    String nombre();
}
