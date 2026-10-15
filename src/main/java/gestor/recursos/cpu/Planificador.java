package gestor.recursos.cpu;

import gestor.modelo.Proceso;
import java.util.List;

/** Estrategia de selección y expropiación de los procesos listos. */
public interface Planificador {
    void encolar(Proceso proceso);

    Proceso siguiente();

    boolean debeExpropiar(Proceso enEjecucion, int quantum);

    List<Proceso> cola();

    void vaciar();

    String nombre();
}
