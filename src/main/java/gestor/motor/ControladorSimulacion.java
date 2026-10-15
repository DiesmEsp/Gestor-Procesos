package gestor.motor;

import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.DefinicionProceso;
import gestor.modelo.Escenario;
import gestor.modelo.PoliticaMemoria;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Único propietario del motor. El reloj y todos los controles comparten este monitor. */
public final class ControladorSimulacion implements AutoCloseable {
    private final Supplier<Escenario> proveedor;
    private Escenario ultimoEscenarioValido;
    private ConfiguracionSimulacion configuracion;
    private Motor motor;
    private ModoSimulacion modo = ModoSimulacion.DETENIDA;
    private int velocidadMs = 500;
    private boolean quantumModificado;
    private boolean memoriaModificada;
    private boolean politicaModificada;
    private String aviso;
    private boolean cerrado;
    private long proximoTick;

    public ControladorSimulacion(Supplier<Escenario> proveedor) {
        this(proveedor, true);
    }

    /** La variante sin reloj permite ejecutar pruebas deterministas mediante avanzarManual(). */
    public ControladorSimulacion(Supplier<Escenario> proveedor, boolean relojAutomatico) {
        this.proveedor = Objects.requireNonNull(proveedor, "El proveedor no puede ser nulo");
        ultimoEscenarioValido = Objects.requireNonNull(proveedor.get(), "El escenario no puede ser nulo");
        configuracion = ultimoEscenarioValido.configuracion();
        recrearMotor();
        if (relojAutomatico) Thread.ofPlatform().daemon().name("reloj-simulacion").start(this::ejecutarReloj);
    }

    public synchronized void iniciar() {
        asegurarAbierto();
        if (modo != ModoSimulacion.DETENIDA && modo != ModoSimulacion.PAUSADA) {
            throw new IllegalStateException("Solo se puede iniciar una simulación detenida o pausada");
        }
        modo = ModoSimulacion.EN_CURSO;
        programarTick();
    }

    public synchronized void pausar() {
        asegurarAbierto();
        if (modo != ModoSimulacion.EN_CURSO) throw new IllegalStateException("Solo se puede pausar una simulación en curso");
        modo = ModoSimulacion.PAUSADA;
        notifyAll();
    }

    public synchronized void avanzarManual() {
        asegurarAbierto();
        if (modo != ModoSimulacion.EN_CURSO) throw new IllegalStateException("Solo se puede avanzar una simulación en curso");
        avanzar();
        programarTick();
    }

    public synchronized void cambiarQuantum(int quantum) {
        asegurarAbierto();
        motor.cambiarQuantum(quantum);
        configuracion = configuracion.conQuantum(quantum);
        quantumModificado = true;
    }

    public synchronized void cambiarMemoriaTotal(int memoriaTotal) {
        asegurarAbierto();
        if (modo != ModoSimulacion.DETENIDA) {
            throw new IllegalStateException("La capacidad de RAM solo puede modificarse antes de iniciar");
        }
        var nueva = configuracion.conMemoriaTotal(memoriaTotal);
        var excedido = procesoQueExcede(ultimoEscenarioValido, memoriaTotal);
        if (excedido != null) throw new IllegalArgumentException("El proceso '" + excedido.id() + "' no cabe en " + memoriaTotal + " MB de RAM");
        configuracion = nueva;
        memoriaModificada = true;
        recrearMotor();
    }

    public synchronized void cambiarPoliticaMemoria(PoliticaMemoria politica) {
        asegurarAbierto();
        if (modo != ModoSimulacion.DETENIDA) {
            throw new IllegalStateException("La política de memoria solo puede modificarse antes de iniciar");
        }
        configuracion = configuracion.conPoliticaMemoria(politica);
        politicaModificada = true;
        recrearMotor();
    }

    public synchronized void cambiarVelocidad(int milisegundos) {
        asegurarAbierto();
        if (milisegundos < 50 || milisegundos > 2000) throw new IllegalArgumentException("La velocidad debe estar entre 50 y 2000 ms por tick");
        velocidadMs = milisegundos;
        programarTick();
    }

    /** Relee el proveedor sin perder el último escenario válido ni las preferencias explícitas. */
    public synchronized void reiniciar() {
        asegurarAbierto();
        aviso = null;
        try {
            ultimoEscenarioValido = Objects.requireNonNull(proveedor.get(), "El escenario no puede ser nulo");
        } catch (RuntimeException excepcion) {
            aviso = "No se pudo releer el escenario; se utiliza el último válido. " + excepcion.getMessage();
        }
        var delArchivo = ultimoEscenarioValido.configuracion();
        int memoria = memoriaModificada ? configuracion.memoriaTotal() : delArchivo.memoriaTotal();
        var excedido = procesoQueExcede(ultimoEscenarioValido, memoria);
        if (excedido != null) {
            String mensaje = "La RAM conservada no aloja al proceso '" + excedido.id() + "'; se utiliza la RAM del archivo";
            aviso = aviso == null ? mensaje : aviso + ". " + mensaje;
            memoria = delArchivo.memoriaTotal();
            memoriaModificada = false;
        }
        configuracion = new ConfiguracionSimulacion(memoria,
                quantumModificado ? configuracion.quantum() : delArchivo.quantum(), delArchivo.maxAdelantamientos(),
                politicaModificada ? configuracion.politicaMemoria() : delArchivo.politicaMemoria(), delArchivo.planificador());
        recrearMotor();
        modo = ModoSimulacion.DETENIDA;
        notifyAll();
    }

    public synchronized Instantanea instantanea() {
        return motor.instantanea().con(modo, velocidadMs, aviso);
    }

    public synchronized void detener() {
        cerrado = true;
        notifyAll();
    }

    @Override
    public synchronized void close() { detener(); }

    private void asegurarAbierto() {
        if (cerrado) throw new IllegalStateException("El controlador está cerrado");
    }

    private static DefinicionProceso procesoQueExcede(Escenario escenario, int memoria) {
        return escenario.procesos().stream().filter(proceso -> proceso.memoria() > memoria).findFirst().orElse(null);
    }

    private void recrearMotor() {
        motor = new Motor(new Escenario(configuracion, ultimoEscenarioValido.procesos()),
                FabricaRecursos.crearPolitica(configuracion.politicaMemoria()), FabricaRecursos.crearPlanificador(configuracion.planificador()));
    }

    private void programarTick() {
        proximoTick = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(velocidadMs);
        notifyAll();
    }

    private void avanzar() {
        motor.avanzarTick();
        if (motor.finalizada()) modo = ModoSimulacion.FINALIZADA;
    }

    private synchronized void ejecutarReloj() {
        try {
            while (!cerrado) {
                if (modo != ModoSimulacion.EN_CURSO) {
                    wait();
                    continue;
                }
                long restante = proximoTick - System.nanoTime();
                if (restante > 0) {
                    TimeUnit.NANOSECONDS.timedWait(this, restante);
                    continue;
                }
                avanzar();
                programarTick();
            }
        } catch (InterruptedException excepcion) {
            cerrado = true;
            Thread.currentThread().interrupt();
        }
    }
}
