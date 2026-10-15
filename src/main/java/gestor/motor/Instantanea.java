package gestor.motor;

import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.EstadoProceso;
import java.util.List;

/** Captura inmutable de un instante completo; nunca contiene referencias a PCB mutables. */
public record Instantanea(int instante, ModoSimulacion modo, ConfiguracionSimulacion configuracion,
        int velocidadMs, Colas colas, List<ResumenProceso> procesos, EstadoMemoria memoria,
        List<TramoGantt> gantt, List<EventoBitacora> bitacora, Metricas metricas, String aviso) {
    public Instantanea {
        procesos = List.copyOf(procesos);
        gantt = List.copyOf(gantt);
        bitacora = List.copyOf(bitacora);
    }

    public Instantanea con(ModoSimulacion modo, int velocidadMs, String aviso) {
        return new Instantanea(instante, modo, configuracion, velocidadMs, colas, procesos, memoria,
                gantt, bitacora, metricas, aviso);
    }

    public record ProcesoEnEspera(String id, int memoria, int adelantamientos, boolean bloqueante) {}
    public record ProcesoEnEjecucion(String id, int ticksQuantumUsados) {}
    public record ProcesoBloqueado(String id, int ticksRestantesES) {}
    public record BloqueMemoria(int base, int limite, int tamano, String idProceso) {}
    public record ResumenProceso(String id, EstadoProceso estado, int tiempoLlegada, int tiempoRafaga,
            int ticksConsumidos, int ticksRestantes, int memoria, Integer prioridad, BloqueMemoria bloque,
            int tiempoEspera, int ticksES, int adelantamientos, boolean bloqueante, Integer ticksRestantesES,
            Integer instanteFinalizacion, Integer tiempoRetorno) {}

    public record EstadoMemoria(int total, int libre, int mayorHueco, int fragmentacionExterna,
            List<BloqueMemoria> bloques) {
        public EstadoMemoria { bloques = List.copyOf(bloques); }
    }

    public record Colas(List<ProcesoEnEspera> nuevos, List<String> listos, ProcesoEnEjecucion ejecucion,
            List<ProcesoBloqueado> bloqueados, List<String> terminados) {
        public Colas {
            nuevos = List.copyOf(nuevos);
            listos = List.copyOf(listos);
            bloqueados = List.copyOf(bloqueados);
            terminados = List.copyOf(terminados);
        }
    }
}
