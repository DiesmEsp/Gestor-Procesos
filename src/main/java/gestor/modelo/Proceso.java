package gestor.modelo;

import java.util.Objects;
import java.util.Optional;

/** PCB mutable, administrado exclusivamente por el motor de simulación. */
public final class Proceso {
    private final DefinicionProceso definicion;
    private EstadoProceso estado = EstadoProceso.PENDIENTE_LLEGADA;
    private int ticksConsumidos;
    private int indiceSiguienteES;
    private int ticksQuantumUsados;
    private int finBloqueo;
    private int baseBloque = -1;
    private int tamanoBloque;
    private int ticksEsperaListo;
    private int ticksEsperaMemoria;
    private int ticksES;
    private int adelantamientos;
    private Integer instanteFinalizacion;

    /**
     * Crea un PCB pendiente de llegada sin memoria ni tiempo consumido.
     *
     * @param definicion datos inmutables del proceso
     */
    public Proceso(DefinicionProceso definicion) {
        this.definicion = Objects.requireNonNull(definicion, "La definición del proceso no puede ser nula");
    }

    private Proceso(Proceso origen) {
        definicion = origen.definicion;
        estado = origen.estado;
        ticksConsumidos = origen.ticksConsumidos;
        indiceSiguienteES = origen.indiceSiguienteES;
        ticksQuantumUsados = origen.ticksQuantumUsados;
        finBloqueo = origen.finBloqueo;
        baseBloque = origen.baseBloque;
        tamanoBloque = origen.tamanoBloque;
        ticksEsperaListo = origen.ticksEsperaListo;
        ticksEsperaMemoria = origen.ticksEsperaMemoria;
        ticksES = origen.ticksES;
        adelantamientos = origen.adelantamientos;
        instanteFinalizacion = origen.instanteFinalizacion;
    }

    /**
     * Copia independiente del PCB para consultar el motor sin modificar su estado.
     *
     * @return una copia independiente de esta instancia
     */
    public Proceso copia() {
        return new Proceso(this);
    }

    /**
     * Consulta los datos inmutables de entrada.
     *
     * @return los datos inmutables de entrada
     */
    public DefinicionProceso definicion() {
        return definicion;
    }

    /**
     * Consulta el estado actual del PCB.
     *
     * @return el estado actual del PCB
     */
    public EstadoProceso estado() {
        return estado;
    }

    /**
     * Transiciona al estado indicado; rechaza los saltos inválidos del ciclo de vida.
     *
     * @param destino estado solicitado para la transición
     */
    public void cambiarEstado(EstadoProceso destino) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new IllegalStateException("Transición inválida de " + estado + " a " + destino
                    + " para el proceso " + definicion.id());
        }
        estado = destino;
    }

    /**
     * Consulta los ticks acumulados de CPU.
     *
     * @return los ticks acumulados de CPU
     */
    public int ticksConsumidos() {
        return ticksConsumidos;
    }

    /**
     * Fija los ticks acumulados de CPU.
     *
     * @param ticksConsumidos ticks de CPU acumulados
     */
    public void fijarTicksConsumidos(int ticksConsumidos) {
        this.ticksConsumidos = exigirNoNegativo(ticksConsumidos, "Los ticks consumidos");
    }

    /**
     * Consulta el índice del siguiente evento de E/S.
     *
     * @return el índice del siguiente evento de E/S
     */
    public int indiceSiguienteES() {
        return indiceSiguienteES;
    }

    /**
     * Fija el índice del siguiente evento de E/S.
     *
     * @param indiceSiguienteES índice no negativo del próximo evento de E/S
     */
    public void fijarIndiceSiguienteES(int indiceSiguienteES) {
        this.indiceSiguienteES = exigirNoNegativo(indiceSiguienteES, "El índice del siguiente evento de E/S");
    }

    /**
     * Consulta los ticks consumidos en el turno actual.
     *
     * @return los ticks consumidos en el turno actual
     */
    public int ticksQuantumUsados() {
        return ticksQuantumUsados;
    }

    /**
     * Fija los ticks consumidos en el turno actual.
     *
     * @param ticksQuantumUsados ticks consumidos en el turno actual
     */
    public void fijarTicksQuantumUsados(int ticksQuantumUsados) {
        this.ticksQuantumUsados = exigirNoNegativo(ticksQuantumUsados, "Los ticks del quantum");
    }

    /**
     * Consulta el instante programado de fin de E/S.
     *
     * @return el instante programado de fin de E/S
     */
    public int finBloqueo() {
        return finBloqueo;
    }

    /**
     * Fija el instante programado de fin de E/S.
     *
     * @param finBloqueo instante no negativo de retorno desde E/S
     */
    public void fijarFinBloqueo(int finBloqueo) {
        this.finBloqueo = exigirNoNegativo(finBloqueo, "El instante de fin de bloqueo");
    }

    /**
     * Consulta la dirección base del bloque, o menos uno sin asignación.
     *
     * @return la dirección base del bloque, o menos uno sin asignación
     */
    public int baseBloque() {
        return baseBloque;
    }

    /**
     * Consulta el tamaño asignado en MB, o cero sin asignación.
     *
     * @return el tamaño asignado en MB, o cero sin asignación
     */
    public int tamanoBloque() {
        return tamanoBloque;
    }

    /**
     * Guarda la base y el tamaño de la partición asignada por el motor.
     *
     * @param base dirección inicial del bloque, incluida
     * @param tamano tamaño positivo del bloque en MB
     */
    public void asignarBloque(int base, int tamano) {
        if (base < 0 || tamano < 1) {
            throw new IllegalArgumentException("El bloque debe tener base no negativa y tamaño positivo");
        }
        baseBloque = base;
        tamanoBloque = tamano;
    }

    /**
     * Borra la referencia a la partición liberada.
     */
    public void quitarBloque() {
        baseBloque = -1;
        tamanoBloque = 0;
    }

    /**
     * Indica si el proceso conserva una partición asignada.
     *
     * @return verdadero si existe una partición asignada
     */
    public boolean tieneMemoria() {
        return tamanoBloque > 0;
    }

    /**
     * Consulta los ticks de espera en Listo.
     *
     * @return los ticks de espera en Listo
     */
    public int ticksEsperaListo() {
        return ticksEsperaListo;
    }

    /**
     * Fija los ticks de espera en Listo.
     *
     * @param ticksEsperaListo ticks no negativos acumulados en Listo
     */
    public void fijarTicksEsperaListo(int ticksEsperaListo) {
        this.ticksEsperaListo = exigirNoNegativo(ticksEsperaListo, "Los ticks de espera en Listo");
    }

    /**
     * Consulta los ticks de espera de RAM.
     *
     * @return los ticks de espera de RAM
     */
    public int ticksEsperaMemoria() {
        return ticksEsperaMemoria;
    }

    /**
     * Fija los ticks de espera de RAM.
     *
     * @param ticksEsperaMemoria ticks no negativos en espera de RAM
     */
    public void fijarTicksEsperaMemoria(int ticksEsperaMemoria) {
        this.ticksEsperaMemoria = exigirNoNegativo(ticksEsperaMemoria, "Los ticks de espera de memoria");
    }

    /**
     * Consulta los ticks acumulados de E/S.
     *
     * @return los ticks acumulados de E/S
     */
    public int ticksES() {
        return ticksES;
    }

    /**
     * Fija los ticks acumulados de E/S.
     *
     * @param ticksES ticks acumulados en Bloqueado
     */
    public void fijarTicksES(int ticksES) {
        this.ticksES = exigirNoNegativo(ticksES, "Los ticks de E/S");
    }

    /**
     * Consulta las admisiones posteriores que adelantaron al proceso.
     *
     * @return las admisiones posteriores que adelantaron al proceso
     */
    public int adelantamientos() {
        return adelantamientos;
    }

    /**
     * Fija las admisiones posteriores que adelantaron al proceso.
     *
     * @param adelantamientos admisiones de procesos posteriores mientras se espera RAM
     */
    public void fijarAdelantamientos(int adelantamientos) {
        this.adelantamientos = exigirNoNegativo(adelantamientos, "Los adelantamientos");
    }

    /**
     * Consulta el instante de finalización, o nulo si todavía no terminó.
     *
     * @return el instante de finalización, o nulo si todavía no terminó
     */
    public Integer instanteFinalizacion() {
        return instanteFinalizacion;
    }

    /**
     * Fija el instante de finalización, o nulo si todavía no terminó.
     *
     * @param instanteFinalizacion instante de finalización; nulo mientras no termine
     */
    public void fijarInstanteFinalizacion(Integer instanteFinalizacion) {
        if (instanteFinalizacion != null) {
            exigirNoNegativo(instanteFinalizacion, "El instante de finalización");
        }
        this.instanteFinalizacion = instanteFinalizacion;
    }

    /**
     * Ticks restantes = tiempo de ráfaga − ticks de CPU consumidos.
     *
     * @return los ticks de CPU restantes
     */
    public int ticksRestantes() {
        return definicion.tiempoRafaga() - ticksConsumidos;
    }

    /**
     * FR-028: espera = ticks en Listo + ticks en Espera de Memoria.
     *
     * @return la suma de esperas en Listo y en espera de RAM
     */
    public int tiempoEspera() {
        return ticksEsperaListo + ticksEsperaMemoria;
    }

    /**
     * FR-027: retorno = finalización − llegada; nulo mientras no haya finalización.
     *
     * @return el tiempo de retorno, o nulo hasta finalizar
     */
    public Integer tiempoRetorno() {
        return instanteFinalizacion == null ? null : instanteFinalizacion - definicion.tiempoLlegada();
    }

    /**
     * Consulta el próximo evento de E/S sin consumirlo.
     *
     * @return el próximo evento, o vacío si no quedan eventos
     */
    public Optional<EventoES> siguienteEventoES() {
        if (indiceSiguienteES >= definicion.bloqueosES().size()) {
            return Optional.empty();
        }
        return Optional.of(definicion.bloqueosES().get(indiceSiguienteES));
    }

    private static int exigirNoNegativo(int valor, String campo) {
        if (valor < 0) {
            throw new IllegalArgumentException(campo + ": el valor no puede ser negativo");
        }
        return valor;
    }
}
