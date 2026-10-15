package gestor.motor;

import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.EstadoProceso;
import gestor.modelo.Escenario;
import gestor.modelo.EventoES;
import gestor.modelo.Proceso;
import gestor.recursos.cpu.Planificador;
import gestor.recursos.memoria.Bloque;
import gestor.recursos.memoria.MapaMemoria;
import gestor.recursos.memoria.PoliticaAsignacion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Motor de ticks determinista, sin hilos ni E/S; el controlador es su único propietario. */
public final class Motor {
    private ConfiguracionSimulacion configuracion;
    private final List<Proceso> procesos;
    private final MapaMemoria mapaMemoria;
    private final Planificador planificador;
    private final List<Proceso> esperaMemoria = new ArrayList<>();
    private final List<Proceso> bloqueados = new ArrayList<>();
    private final Bitacora bitacora = new Bitacora();
    private final DiagramaGantt gantt = new DiagramaGantt();
    private Proceso enEjecucion;
    private Proceso expropiadoPendiente;
    private int quantumTurno;
    private int instanteActual;
    private int ticksCpuOcupada;

    /** Recibe un escenario validado y recursos cuyo uso queda a cargo del motor. */
    public Motor(Escenario escenario, PoliticaAsignacion politica, Planificador planificador) {
        Objects.requireNonNull(escenario, "El escenario no puede ser nulo");
        configuracion = escenario.configuracion();
        procesos = escenario.procesos().stream().map(Proceso::new).toList();
        mapaMemoria = new MapaMemoria(configuracion.memoriaTotal(), politica);
        this.planificador = Objects.requireNonNull(planificador, "El planificador no puede ser nulo");
    }

    /**
     * Ejecuta el orden del Glosario y Modelo de Tiempo de la especificación:
     * admisión pendiente, llegadas, fin de E/S, expropiado, despacho, tick y evaluación.
     * Un motor finalizado conserva su instante final y sus métricas.
     */
    public void avanzarTick() {
        if (finalizada()) {
            return;
        }

        admitirEnEspera();                     // 1. Espera de memoria anterior a las llegadas.
        recibirLlegadas();                    // 2. Llegadas simultáneas por ordenArchivo.
        completarEntradaSalida();             // 3. Retornos en orden de bloqueo.
        reencolarExpropiado();                 // 4. Expropiado al final de la cola de Listos.
        boolean nuevoTurno = despachar();      // 5. Quantum fijado al despachar.
        ejecutarTick(nuevoTurno);              // 6. CPU, esperas y E/S de este intervalo.
        evaluarFinDeTick();                    // 7. Fin de ráfaga, E/S y quantum, por precedencia.
        instanteActual++;
    }

    public void cambiarQuantum(int quantum) {
        if (quantum < 1) {
            throw new IllegalArgumentException("El quantum debe ser mayor o igual a 1 tick");
        }
        configuracion = configuracion.conQuantum(quantum);
    }

    private void admitirEnEspera() {
        var pendientes = esperaMemoria.iterator();
        while (pendientes.hasNext()) {
            if (admitir(pendientes.next())) {
                pendientes.remove();
            }
        }
    }

    private void recibirLlegadas() {
        List<Proceso> llegadas = procesos.stream()
                .filter(proceso -> proceso.estado() == EstadoProceso.PENDIENTE_LLEGADA
                        && proceso.definicion().tiempoLlegada() == instanteActual)
                .sorted(Comparator.comparingInt(proceso -> proceso.definicion().ordenArchivo())).toList();
        for (Proceso proceso : llegadas) {
            transicionar(proceso, EstadoProceso.NUEVO, instanteActual, "Llegada");
            if (!admitir(proceso)) {
                esperaMemoria.add(proceso);
                // Evento informativo de espera; no solicita una transición NUEVO -> NUEVO al PCB.
                bitacora.registrar(instanteActual, proceso.definicion().id(), EstadoProceso.NUEVO,
                        EstadoProceso.NUEVO, "Sin memoria contigua suficiente");
            }
        }
    }

    private boolean admitir(Proceso proceso) {
        var asignacion = mapaMemoria.asignar(proceso.definicion().id(), proceso.definicion().memoria());
        if (asignacion.isEmpty()) {
            return false;
        }
        Bloque bloque = asignacion.orElseThrow();
        proceso.asignarBloque(bloque.base(), bloque.tamano());
        transicionar(proceso, EstadoProceso.LISTO, instanteActual,
                "Memoria asignada [" + bloque.base() + "–" + bloque.limite() + "]");
        planificador.encolar(proceso);
        return true;
    }

    private void completarEntradaSalida() {
        var pendientes = bloqueados.iterator();
        while (pendientes.hasNext()) {
            Proceso proceso = pendientes.next();
            if (proceso.finBloqueo() == instanteActual) {
                pendientes.remove();
                transicionar(proceso, EstadoProceso.LISTO, instanteActual, "Fin de E/S");
                planificador.encolar(proceso);
            }
        }
    }

    private void reencolarExpropiado() {
        if (expropiadoPendiente != null) {
            transicionar(expropiadoPendiente, EstadoProceso.LISTO, instanteActual, "Quantum agotado");
            planificador.encolar(expropiadoPendiente);
            expropiadoPendiente = null;
        }
    }

    private boolean despachar() {
        if (enEjecucion != null) {
            return false;
        }
        enEjecucion = planificador.siguiente();
        if (enEjecucion == null) {
            return false;
        }
        enEjecucion.fijarTicksQuantumUsados(0);
        quantumTurno = configuracion.quantum();
        transicionar(enEjecucion, EstadoProceso.EJECUCION, instanteActual, "Despacho");
        return true;
    }

    private void ejecutarTick(boolean nuevoTurno) {
        for (Proceso proceso : planificador.cola()) {
            proceso.fijarTicksEsperaListo(proceso.ticksEsperaListo() + 1);
        }
        for (Proceso proceso : esperaMemoria) {
            proceso.fijarTicksEsperaMemoria(proceso.ticksEsperaMemoria() + 1);
        }
        for (Proceso proceso : bloqueados) {
            proceso.fijarTicksES(proceso.ticksES() + 1);
        }

        String idProceso = null;
        if (enEjecucion != null) {
            idProceso = enEjecucion.definicion().id();
            enEjecucion.fijarTicksConsumidos(enEjecucion.ticksConsumidos() + 1);
            enEjecucion.fijarTicksQuantumUsados(enEjecucion.ticksQuantumUsados() + 1);
            ticksCpuOcupada++;
        }
        gantt.registrarTick(instanteActual, idProceso, nuevoTurno);
    }

    private void evaluarFinDeTick() {
        if (enEjecucion == null) {
            return;
        }
        int finTick = instanteActual + 1;
        if (enEjecucion.ticksRestantes() == 0) {
            transicionar(enEjecucion, EstadoProceso.TERMINADO, finTick, "Ráfaga completada");
            enEjecucion.fijarInstanteFinalizacion(finTick);
            mapaMemoria.liberar(enEjecucion.definicion().id());
            enEjecucion.quitarBloque();
            enEjecucion = null;
            return;
        }

        var siguienteEvento = enEjecucion.siguienteEventoES();
        if (siguienteEvento.isPresent() && siguienteEvento.orElseThrow().enQuantum() == enEjecucion.ticksConsumidos()) {
            EventoES evento = siguienteEvento.orElseThrow();
            enEjecucion.fijarFinBloqueo(finTick + evento.duracion());
            enEjecucion.fijarIndiceSiguienteES(enEjecucion.indiceSiguienteES() + 1);
            transicionar(enEjecucion, EstadoProceso.BLOQUEADO, finTick, "Solicita E/S (" + evento.duracion() + " ticks)");
            bloqueados.add(enEjecucion);
            enEjecucion = null;
        } else if (planificador.debeExpropiar(enEjecucion, quantumTurno)) {
            // La transición y el reencolado se registran tras las llegadas y los retornos del próximo instante.
            expropiadoPendiente = enEjecucion;
            enEjecucion = null;
        }
    }

    private void transicionar(Proceso proceso, EstadoProceso destino, int instante, String motivo) {
        EstadoProceso origen = proceso.estado();
        proceso.cambiarEstado(destino);
        bitacora.registrar(instante, proceso.definicion().id(), origen, destino, motivo);
    }

    public int instanteActual() {
        return instanteActual;
    }

    public boolean finalizada() {
        return procesos.stream().allMatch(proceso -> proceso.estado() == EstadoProceso.TERMINADO);
    }

    public int ticksCpuOcupada() {
        return ticksCpuOcupada;
    }

    /** Lista inmodificable de copias separadas de los PCB, estable entre ticks. */
    public List<Proceso> procesos() {
        return procesos.stream().map(Proceso::copia).toList();
    }

    public List<EventoBitacora> bitacora() {
        return bitacora.eventos();
    }

    public List<TramoGantt> gantt() {
        return gantt.tramos();
    }

    /** Mapa separado del motor; modificar esta copia no altera la simulación. */
    public MapaMemoria mapaMemoria() {
        return mapaMemoria.copia();
    }

    public Metricas metricas() {
        return Metricas.calcular(procesos, ticksCpuOcupada, instanteActual);
    }
}
