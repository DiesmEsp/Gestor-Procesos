package gestor.motor;

import gestor.modelo.EstadoProceso;
import gestor.modelo.EventoES;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import gestor.recursos.memoria.Bloque;
import java.util.List;
import static gestor.pruebas.EscenariosMotor.*;

/** Referencias de la Historia 1, escenarios 1, 4 y 5; determinismo FR-032. */
public class MotorRoundRobinPrueba {
    @Prueba
    public void reproduceElEscenarioUnoDeRoundRobin() {
        Motor motor = crear(100, 2, definir("P1", 0, 4, 30, 0), definir("P2", 1, 3, 20, 1));

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 2), new TramoGantt("P2", 2, 4),
                new TramoGantt("P1", 4, 6), new TramoGantt("P2", 6, 7)), motor.gantt(),
                "El Gantt debe coincidir exactamente con el escenario 1 de la Historia 1");
        Afirmar.iguales(6, proceso(motor, "P1").instanteFinalizacion(), "P1 termina en 6");
        Afirmar.iguales(7, proceso(motor, "P2").instanteFinalizacion(), "P2 termina en 7");
        Afirmar.iguales(6, proceso(motor, "P1").tiempoRetorno(), "Retorno de P1");
        Afirmar.iguales(6, proceso(motor, "P2").tiempoRetorno(), "Retorno de P2");
        Afirmar.iguales(2, proceso(motor, "P1").tiempoEspera(), "Espera de P1");
        Afirmar.iguales(3, proceso(motor, "P2").tiempoEspera(), "Espera de P2");
        Afirmar.iguales(7, motor.instanteActual(), "El reloj termina con el último proceso");
        Afirmar.iguales(7, motor.ticksCpuOcupada(), "Los siete ticks tienen CPU ocupada");
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), motor.mapaMemoria().bloques(),
                "Todos los procesos liberan su memoria al terminar");
    }

    @Prueba
    public void terminarPrevaleceSobreAgotarElQuantum() {
        Motor motor = crear(100, 2, definir("P1", 0, 2, 10, 0), definir("P2", 0, 1, 10, 1));
        avanzar(motor, 2);

        Afirmar.iguales(EstadoProceso.TERMINADO, proceso(motor, "P1").estado(), "P1 ya completó la ráfaga");
        Afirmar.falso(proceso(motor, "P1").tieneMemoria(), "La memoria se libera en el instante final");
        finalizar(motor);
        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 2), new TramoGantt("P2", 2, 3)), motor.gantt(),
                "P1 no debe recibir un turno extra después de terminar");
        Afirmar.falso(motor.bitacora().stream().anyMatch(evento -> evento.idProceso().equals("P1")
                && evento.motivo().equals("Quantum agotado")), "El fin de ráfaga prevalece sobre la expropiación");
    }

    @Prueba
    public void ordenaNuevoRetornadoDeEntradaSalidaYExpropiadoEnElMismoInstante() {
        Motor motor = crear(100, 1, definir("ES", 0, 3, 10, 0, new EventoES(1, 1)),
                definir("CPU", 0, 3, 10, 1), definir("NUEVO", 2, 1, 10, 2));

        avanzar(motor, 3);

        List<String> admitidos = motor.bitacora().stream()
                .filter(evento -> evento.instante() == 2 && evento.destino() == EstadoProceso.LISTO)
                .map(EventoBitacora::idProceso).toList();
        Afirmar.iguales(List.of("NUEVO", "ES", "CPU"), admitidos,
                "En el instante 2 se encolan llegada, fin de E/S y expropiado en ese orden");
        Afirmar.iguales(List.of(new TramoGantt("ES", 0, 1), new TramoGantt("CPU", 1, 2),
                new TramoGantt("NUEVO", 2, 3)), motor.gantt(), "El recién llegado recibe la CPU primero");
    }

    @Prueba
    public void agrupaLosTicksOciososAntesDeLaPrimeraLlegada() {
        Motor motor = crear(100, 1, definir("P1", 3, 1, 10, 0));
        avanzar(motor, 2);
        Afirmar.iguales(EstadoProceso.PENDIENTE_LLEGADA, proceso(motor, "P1").estado(),
                "No se admite un proceso antes de su llegada");
        Afirmar.iguales(List.of(), motor.bitacora(), "No hay transiciones durante la espera inicial");
        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt(null, 0, 3), new TramoGantt("P1", 3, 4)), motor.gantt(),
                "Los ticks ociosos iniciales forman un solo tramo");
        Afirmar.iguales(0, proceso(motor, "P1").tiempoEspera(), "La espera anterior a la llegada no cuenta");
    }

    @Prueba
    public void desempataLasLlegadasSimultaneasPorOrdenArchivo() {
        Motor motor = crear(100, 1, definir("C", 0, 1, 10, 2), definir("A", 0, 1, 10, 0),
                definir("B", 0, 1, 10, 1));

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("A", 0, 1), new TramoGantt("B", 1, 2),
                new TramoGantt("C", 2, 3)), motor.gantt(), "El ordenArchivo manda sobre el orden de la lista recibida");
        Afirmar.iguales(List.of("A", "B", "C"), motor.bitacora().stream()
                .filter(evento -> evento.motivo().equals("Llegada")).map(EventoBitacora::idProceso).toList(),
                "También la bitácora respeta el orden del archivo");
    }

    @Prueba
    public void reducirElQuantumNoRecortaElTurnoActual() {
        Motor motor = crear(100, 3, definir("P1", 0, 5, 10, 0), definir("P2", 0, 2, 10, 1));
        avanzar(motor, 1);

        motor.cambiarQuantum(1);
        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 3), new TramoGantt("P2", 3, 4),
                new TramoGantt("P1", 4, 5), new TramoGantt("P2", 5, 6), new TramoGantt("P1", 6, 7)),
                motor.gantt(), "El turno en curso conserva 3 ticks; los siguientes usan 1");
    }

    @Prueba
    public void aumentarElQuantumNoAlargaElTurnoActual() {
        Motor motor = crear(100, 2, definir("P1", 0, 5, 10, 0), definir("P2", 0, 3, 10, 1));
        avanzar(motor, 1);

        motor.cambiarQuantum(4);
        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 2), new TramoGantt("P2", 2, 5),
                new TramoGantt("P1", 5, 8)), motor.gantt(), "El cambio solo se aplica a partir del siguiente despacho");
    }

    @Prueba
    public void rechazaQuantumNoPositivoSinCambiarElConfigurado() {
        Motor motor = crear(100, 2, definir("P1", 0, 3, 10, 0));
        Afirmar.lanza(IllegalArgumentException.class, () -> motor.cambiarQuantum(0));
        Afirmar.lanza(IllegalArgumentException.class, () -> motor.cambiarQuantum(-1));

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 2), new TramoGantt("P1", 2, 3)), motor.gantt(),
                "Un rechazo conserva el quantum anterior y los límites entre turnos del mismo proceso");
    }

    @Prueba
    public void registraLlegadaYAdmisionEnElMismoInstante() {
        Motor motor = crear(100, 2, definir("P1", 0, 1, 30, 0));

        avanzar(motor, 1);

        Afirmar.iguales(List.of(
                new EventoBitacora(0, "P1", EstadoProceso.PENDIENTE_LLEGADA, EstadoProceso.NUEVO, "Llegada"),
                new EventoBitacora(0, "P1", EstadoProceso.NUEVO, EstadoProceso.LISTO, "Memoria asignada [0–29]"),
                new EventoBitacora(0, "P1", EstadoProceso.LISTO, EstadoProceso.EJECUCION, "Despacho"),
                new EventoBitacora(1, "P1", EstadoProceso.EJECUCION, EstadoProceso.TERMINADO, "Ráfaga completada")),
                motor.bitacora(), "Se registra cada transición con su instante y motivo exactos");
    }

    @Prueba
    public void soloFinalizaCuandoTerminanTodosYNoAvanzaDespues() {
        Motor motor = crear(100, 1, definir("P1", 0, 1, 10, 0), definir("P2", 3, 1, 10, 1));
        Afirmar.falso(motor.finalizada(), "El motor nuevo tiene procesos pendientes");
        avanzar(motor, 1);
        Afirmar.falso(motor.finalizada(), "P1 terminó pero P2 todavía no llegó");
        finalizar(motor);
        var eventos = motor.bitacora();
        var gantt = motor.gantt();
        var metricas = motor.metricas();

        avanzar(motor, 2);

        Afirmar.iguales(4, motor.instanteActual(), "Un motor finalizado conserva el instante final");
        Afirmar.iguales(eventos, motor.bitacora(), "No se agregan eventos después de finalizar");
        Afirmar.iguales(gantt, motor.gantt(), "No se agregan ticks ociosos después del final");
        Afirmar.iguales(metricas, motor.metricas(), "La utilización no disminuye después de finalizar");
        Afirmar.verdadero(motor.procesos().stream().allMatch(proceso -> proceso.estado() == EstadoProceso.TERMINADO),
                "Todos los procesos están terminados");
    }

    @Prueba
    public void repetirElEscenarioProduceLasMismasBitacoraMetricasYGantt() {
        var primero = definir("P1", 0, 4, 30, 0, new EventoES(1, 2));
        var segundo = definir("P2", 1, 3, 20, 1);
        Motor primeraEjecucion = crear(100, 2, primero, segundo);
        Motor segundaEjecucion = crear(100, 2, primero, segundo);

        finalizar(primeraEjecucion);
        finalizar(segundaEjecucion);

        Afirmar.iguales(primeraEjecucion.bitacora(), segundaEjecucion.bitacora(), "FR-032: bitácoras idénticas");
        Afirmar.iguales(primeraEjecucion.gantt(), segundaEjecucion.gantt(), "FR-032: diagramas idénticos");
        Afirmar.iguales(primeraEjecucion.metricas(), segundaEjecucion.metricas(), "FR-032: métricas idénticas");
    }

    @Prueba
    public void lasConsultasNoPermitenModificarElEstadoInterno() {
        Motor motor = crear(100, 2, definir("P1", 0, 3, 30, 0));
        var procesosIniciales = motor.procesos();
        Afirmar.lanza(UnsupportedOperationException.class, procesosIniciales::clear);
        procesosIniciales.getFirst().fijarTicksConsumidos(3);
        Afirmar.iguales(0, proceso(motor, "P1").ticksConsumidos(), "El PCB consultado es una copia separada");
        procesosIniciales.getFirst().fijarTicksConsumidos(0);

        avanzar(motor, 1);
        var eventos = motor.bitacora();
        var tramos = motor.gantt();
        var memoria = motor.mapaMemoria();
        Afirmar.lanza(UnsupportedOperationException.class, eventos::clear);
        Afirmar.lanza(UnsupportedOperationException.class, tramos::clear);
        memoria.liberar("P1");
        Afirmar.verdadero(motor.mapaMemoria().bloqueDe("P1").isPresent(),
                "Modificar el mapa consultado no libera la memoria del motor");
        Afirmar.iguales(EstadoProceso.PENDIENTE_LLEGADA, procesosIniciales.getFirst().estado(),
                "Las consultas anteriores tampoco cambian al avanzar el motor");

        finalizar(motor);
        Afirmar.iguales(3, eventos.size(), "La copia previa de la bitácora conserva sus eventos");
        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 1)), tramos, "La copia previa del Gantt es estable");
    }
}
