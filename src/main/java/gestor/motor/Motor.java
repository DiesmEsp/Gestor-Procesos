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

        admitirCandidatos();                   // 1 y 2. Espera anterior y llegadas con protección anti-inanición.
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

    /** Aplica research.md §7: solo los candidatos anteriores aún en espera pueden ser adelantados. */
    private void admitirCandidatos() {
        List<Proceso> candidatos = new ArrayList<>(esperaMemoria);
        List<Proceso> llegadas = procesos.stream()
                .filter(proceso -> proceso.estado() == EstadoProceso.PENDIENTE_LLEGADA
                        && proceso.definicion().tiempoLlegada() == instanteActual)
                .sorted(Comparator.comparingInt(proceso -> proceso.definicion().ordenArchivo())).toList();
        candidatos.addAll(llegadas);
        esperaMemoria.clear();
        boolean bloqueada = false;

        for (Proceso proceso : candidatos) {
            boolean recienLlegado = proceso.estado() == EstadoProceso.PENDIENTE_LLEGADA;
            if (recienLlegado) {
                transicionar(proceso, EstadoProceso.NUEVO, instanteActual, "Llegada");
            }
            if (!bloqueada && admitir(proceso)) {
                // La cola reconstruida contiene únicamente candidatos anteriores que todavía no caben.
                for (Proceso anterior : esperaMemoria) {
                    anterior.fijarAdelantamientos(anterior.adelantamientos() + 1);
                    if (esBloqueante(anterior)) {
                        bloqueada = true;
                    }
                }
                continue;
            }

            esperaMemoria.add(proceso);
            if (recienLlegado) {
                String motivo = bloqueada ? "Admisión detenida por proceso bloqueante" : "Sin memoria contigua suficiente";
                // La espera informa su causa sin solicitar una transición NUEVO -> NUEVO al PCB.
                bitacora.registrar(instanteActual, proceso.definicion().id(), EstadoProceso.NUEVO,
                        EstadoProceso.NUEVO, motivo);
            }
            if (esBloqueante(proceso)) {
                bloqueada = true;
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

    /** FR-015a: la protección solo está activa mientras el proceso espera memoria en NUEVO. */
    public boolean esBloqueante(Proceso proceso) {
        Objects.requireNonNull(proceso, "El proceso consultado no puede ser nulo");
        return proceso.estado() == EstadoProceso.NUEVO
                && proceso.adelantamientos() >= configuracion.maxAdelantamientos();
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

    /** Captura entre ticks; el contexto del reloj lo completa exclusivamente el controlador. */
    public Instantanea instantanea() {
        var nuevos = esperaMemoria.stream().map(proceso -> new Instantanea.ProcesoEnEspera(
                proceso.definicion().id(), proceso.definicion().memoria(), proceso.adelantamientos(), esBloqueante(proceso))).toList();
        var listos = new ArrayList<>(planificador.cola().stream().map(proceso -> proceso.definicion().id()).toList());
        // La expropiación ya liberó la CPU, aunque su evento se registra tras llegadas y E/S del próximo tick.
        if (expropiadoPendiente != null) listos.add(expropiadoPendiente.definicion().id());
        var ejecucion = enEjecucion == null ? null : new Instantanea.ProcesoEnEjecucion(
                enEjecucion.definicion().id(), enEjecucion.ticksQuantumUsados());
        var enEsperaES = bloqueados.stream().map(proceso -> new Instantanea.ProcesoBloqueado(
                proceso.definicion().id(), proceso.finBloqueo() - instanteActual)).toList();
        var terminados = procesos.stream().filter(proceso -> proceso.estado() == EstadoProceso.TERMINADO)
                .sorted(Comparator.comparingInt(Proceso::instanteFinalizacion))
                .map(proceso -> proceso.definicion().id()).toList();
        var resumenes = procesos.stream().map(proceso -> {
            var definicion = proceso.definicion();
            var bloque = proceso.tieneMemoria() ? new Instantanea.BloqueMemoria(proceso.baseBloque(),
                    proceso.baseBloque() + proceso.tamanoBloque() - 1, proceso.tamanoBloque(), definicion.id()) : null;
            Integer restanteES = proceso.estado() == EstadoProceso.BLOQUEADO ? proceso.finBloqueo() - instanteActual : null;
            EstadoProceso estado = proceso == expropiadoPendiente ? EstadoProceso.LISTO : proceso.estado();
            return new Instantanea.ResumenProceso(definicion.id(), estado, definicion.tiempoLlegada(),
                    definicion.tiempoRafaga(), proceso.ticksConsumidos(), proceso.ticksRestantes(), definicion.memoria(),
                    definicion.prioridad(), bloque, proceso.tiempoEspera(), proceso.ticksES(), proceso.adelantamientos(),
                    esBloqueante(proceso), restanteES, proceso.instanteFinalizacion(), proceso.tiempoRetorno());
        }).toList();
        var memoria = new Instantanea.EstadoMemoria(mapaMemoria.total(), mapaMemoria.memoriaLibre(),
                mapaMemoria.mayorHueco(), mapaMemoria.fragmentacionExterna(), mapaMemoria.bloques().stream()
                .map(bloque -> new Instantanea.BloqueMemoria(bloque.base(), bloque.limite(), bloque.tamano(), bloque.idProceso())).toList());
        return new Instantanea(instanteActual, null, configuracion, 0,
                new Instantanea.Colas(nuevos, listos, ejecucion, enEsperaES, terminados), resumenes, memoria,
                gantt.tramos(), bitacora.eventos(), metricas(), null);
    }
}
