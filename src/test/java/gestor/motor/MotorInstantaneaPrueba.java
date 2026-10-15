package gestor.motor;

import gestor.modelo.EstadoProceso;
import gestor.modelo.EventoES;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import static gestor.pruebas.EscenariosMotor.*;

public class MotorInstantaneaPrueba {
    @Prueba
    public void capturaElInstanteInicialConColasVaciasYContextoNeutro() {
        Motor motor = crear(100, 3, definir("A", 0, 3, 30, 0));
        Instantanea estado = motor.instantanea();
        Afirmar.iguales(0, estado.instante(), "Reloj inicial");
        Afirmar.nulo(estado.modo(), "El motor no conoce el modo del controlador");
        Afirmar.iguales(0, estado.velocidadMs(), "El motor no conoce la velocidad");
        Afirmar.nulo(estado.aviso(), "Sin aviso del controlador");
        Afirmar.iguales(List.of(), estado.colas().nuevos(), "Los pendientes aún no llegaron");
        Afirmar.iguales(List.of(), estado.colas().listos(), "Sin listos");
        Afirmar.nulo(estado.colas().ejecucion(), "Sin CPU asignada");
        Afirmar.iguales(EstadoProceso.PENDIENTE_LLEGADA, estado.procesos().getFirst().estado(), "PCB pendiente");
        Afirmar.iguales(List.of(new Instantanea.BloqueMemoria(0, 99, 100, null)), estado.memoria().bloques(), "RAM inicial");
        Afirmar.iguales(new Metricas(null, null, null, 0, 1), estado.metricas(), "Métricas iniciales");
    }

    @Prueba
    public void capturaCpuListosBloqueMemoriaGanttYBitacora() {
        Motor motor = crear(100, 3, definir("A", 0, 4, 30, 0), definir("B", 0, 2, 20, 1));
        avanzar(motor, 1);
        Instantanea estado = motor.instantanea();
        Afirmar.iguales(new Instantanea.ProcesoEnEjecucion("A", 1), estado.colas().ejecucion(), "CPU y consumo del turno");
        Afirmar.iguales(List.of("B"), estado.colas().listos(), "FIFO de Listos");
        var proceso = estado.procesos().getFirst();
        Afirmar.iguales(new Instantanea.BloqueMemoria(0, 29, 30, "A"), proceso.bloque(), "Bloque del PCB");
        Afirmar.iguales(1, proceso.ticksConsumidos(), "Consumo acumulado");
        Afirmar.iguales(3, proceso.ticksRestantes(), "Ráfaga restante");
        Afirmar.nulo(proceso.ticksRestantesES(), "Un proceso en CPU no tiene E/S pendiente");
        Afirmar.falso(proceso.bloqueante(), "Un proceso admitido no bloquea memoria");
        Afirmar.iguales(50, estado.memoria().libre(), "RAM libre");
        Afirmar.iguales(50, estado.memoria().mayorHueco(), "Mayor hueco");
        Afirmar.iguales(0, estado.memoria().fragmentacionExterna(), "Sin fragmentación externa");
        Afirmar.iguales(motor.gantt(), estado.gantt(), "Gantt completo");
        Afirmar.iguales(motor.bitacora(), estado.bitacora(), "Bitácora completa");
    }

    @Prueba
    public void representaElExpropiadoPendienteComoListoSinCpuAsignada() {
        Motor motor = crear(100, 1, definir("A", 0, 2, 10, 0), definir("B", 0, 2, 10, 1));
        avanzar(motor, 1);
        var estado = motor.instantanea();
        Afirmar.nulo(estado.colas().ejecucion(), "La expropiación libera la CPU");
        Afirmar.iguales(List.of("B", "A"), estado.colas().listos(), "El expropiado es el último listo para el siguiente instante");
        Afirmar.iguales(EstadoProceso.LISTO, estado.procesos().getFirst().estado(), "La consulta no muestra una CPU ficticia");
    }

    @Prueba
    public void capturaBloqueadosTerminadosYTiemposRestantes() {
        Motor motor = crear(100, 2, definir("A", 0, 2, 10, 0, new EventoES(1, 2)), definir("B", 0, 1, 20, 1));
        avanzar(motor, 1);
        var bloqueada = motor.instantanea();
        Afirmar.iguales(List.of(new Instantanea.ProcesoBloqueado("A", 2)), bloqueada.colas().bloqueados(), "Dos ticks de E/S pendientes");
        Afirmar.iguales(2, bloqueada.procesos().getFirst().ticksRestantesES(), "E/S pendiente por proceso");
        avanzar(motor, 1);
        var estado = motor.instantanea();
        Afirmar.iguales(List.of("B"), estado.colas().terminados(), "B terminó antes que A");
        Afirmar.nulo(estado.procesos().get(1).bloque(), "Un terminado ya no tiene bloque");
        finalizar(motor);
        Afirmar.iguales(List.of("B", "A"), motor.instantanea().colas().terminados(), "Orden de finalización");
        Afirmar.iguales(new Metricas(0.5, 3.0, 75.0, 2, 2), motor.instantanea().metricas(), "Promedios y utilización de la captura final");
    }

    @Prueba
    public void exponeElBloqueanteYLosAdelantamientosEnLaColaYEnElResumen() {
        var lector = new gestor.entrada.LectorEscenario();
        var escenario = lector.leer(java.nio.file.Path.of("ejemplos/inanicion.json"));
        Motor motor = new Motor(escenario, FabricaRecursos.crearPolitica(escenario.configuracion().politicaMemoria()),
                FabricaRecursos.crearPlanificador(escenario.configuracion().planificador()));
        avanzar(motor, 1);
        var estado = motor.instantanea();
        Afirmar.iguales(new Instantanea.ProcesoEnEspera("G", 80, 5, true), estado.colas().nuevos().getFirst(), "Cola bloqueante");
        var grande = estado.procesos().stream().filter(proceso -> proceso.id().equals("G")).findFirst().orElseThrow();
        Afirmar.verdadero(grande.bloqueante(), "El resumen también expone la condición de bloqueante");
        Afirmar.iguales(5, grande.adelantamientos(), "El resumen conserva el contador");
    }

    @Prueba
    public void lasInstantaneasSonInmutablesYElContextoSeComponeSinCambiarElOriginal() {
        Motor motor = crear(100, 3, definir("A", 0, 3, 30, 0), definir("B", 0, 1, 20, 1));
        avanzar(motor, 1);
        Instantanea original = motor.instantanea();
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.procesos().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.colas().listos().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.colas().nuevos().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.colas().bloqueados().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.colas().terminados().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.memoria().bloques().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.gantt().clear());
        Afirmar.lanza(UnsupportedOperationException.class, () -> original.bitacora().clear());
        Instantanea compuesta = original.con(ModoSimulacion.PAUSADA, 50, "Aviso");
        Afirmar.iguales(ModoSimulacion.PAUSADA, compuesta.modo(), "Contexto del controlador");
        Afirmar.iguales(50, compuesta.velocidadMs(), "Velocidad compuesta");
        Afirmar.iguales("Aviso", compuesta.aviso(), "Aviso compuesto");
        Afirmar.nulo(original.modo(), "El original conserva su contexto neutro");
        finalizar(motor);
        Afirmar.iguales(1, original.instante(), "La consulta anterior no avanza");
        Afirmar.iguales(1, original.procesos().getFirst().ticksConsumidos(), "El PCB de la consulta tampoco avanza");
        Afirmar.iguales(List.of(new TramoGantt("A", 0, 1)), original.gantt(), "El Gantt anterior es estable");
    }
}
