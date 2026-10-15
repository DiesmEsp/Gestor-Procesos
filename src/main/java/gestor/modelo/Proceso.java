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

    /** Copia independiente del PCB para consultar el motor sin modificar su estado. */
    public Proceso copia() {
        return new Proceso(this);
    }

    public DefinicionProceso definicion() {
        return definicion;
    }

    public EstadoProceso estado() {
        return estado;
    }

    public void cambiarEstado(EstadoProceso destino) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new IllegalStateException("Transición inválida de " + estado + " a " + destino
                    + " para el proceso " + definicion.id());
        }
        estado = destino;
    }

    public int ticksConsumidos() {
        return ticksConsumidos;
    }

    public void fijarTicksConsumidos(int ticksConsumidos) {
        this.ticksConsumidos = exigirNoNegativo(ticksConsumidos, "Los ticks consumidos");
    }

    public int indiceSiguienteES() {
        return indiceSiguienteES;
    }

    public void fijarIndiceSiguienteES(int indiceSiguienteES) {
        this.indiceSiguienteES = exigirNoNegativo(indiceSiguienteES, "El índice del siguiente evento de E/S");
    }

    public int ticksQuantumUsados() {
        return ticksQuantumUsados;
    }

    public void fijarTicksQuantumUsados(int ticksQuantumUsados) {
        this.ticksQuantumUsados = exigirNoNegativo(ticksQuantumUsados, "Los ticks del quantum");
    }

    public int finBloqueo() {
        return finBloqueo;
    }

    public void fijarFinBloqueo(int finBloqueo) {
        this.finBloqueo = exigirNoNegativo(finBloqueo, "El instante de fin de bloqueo");
    }

    public int baseBloque() {
        return baseBloque;
    }

    public int tamanoBloque() {
        return tamanoBloque;
    }

    public void asignarBloque(int base, int tamano) {
        if (base < 0 || tamano < 1) {
            throw new IllegalArgumentException("El bloque debe tener base no negativa y tamaño positivo");
        }
        baseBloque = base;
        tamanoBloque = tamano;
    }

    public void quitarBloque() {
        baseBloque = -1;
        tamanoBloque = 0;
    }

    public boolean tieneMemoria() {
        return tamanoBloque > 0;
    }

    public int ticksEsperaListo() {
        return ticksEsperaListo;
    }

    public void fijarTicksEsperaListo(int ticksEsperaListo) {
        this.ticksEsperaListo = exigirNoNegativo(ticksEsperaListo, "Los ticks de espera en Listo");
    }

    public int ticksEsperaMemoria() {
        return ticksEsperaMemoria;
    }

    public void fijarTicksEsperaMemoria(int ticksEsperaMemoria) {
        this.ticksEsperaMemoria = exigirNoNegativo(ticksEsperaMemoria, "Los ticks de espera de memoria");
    }

    public int ticksES() {
        return ticksES;
    }

    public void fijarTicksES(int ticksES) {
        this.ticksES = exigirNoNegativo(ticksES, "Los ticks de E/S");
    }

    public int adelantamientos() {
        return adelantamientos;
    }

    public void fijarAdelantamientos(int adelantamientos) {
        this.adelantamientos = exigirNoNegativo(adelantamientos, "Los adelantamientos");
    }

    public Integer instanteFinalizacion() {
        return instanteFinalizacion;
    }

    public void fijarInstanteFinalizacion(Integer instanteFinalizacion) {
        if (instanteFinalizacion != null) {
            exigirNoNegativo(instanteFinalizacion, "El instante de finalización");
        }
        this.instanteFinalizacion = instanteFinalizacion;
    }

    /** Ticks restantes = tiempo de ráfaga − ticks de CPU consumidos. */
    public int ticksRestantes() {
        return definicion.tiempoRafaga() - ticksConsumidos;
    }

    /** FR-028: espera = ticks en Listo + ticks en Espera de Memoria. */
    public int tiempoEspera() {
        return ticksEsperaListo + ticksEsperaMemoria;
    }

    /** FR-027: retorno = finalización − llegada; nulo mientras no haya finalización. */
    public Integer tiempoRetorno() {
        return instanteFinalizacion == null ? null : instanteFinalizacion - definicion.tiempoLlegada();
    }

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
