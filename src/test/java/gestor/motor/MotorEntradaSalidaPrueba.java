package gestor.motor;

import gestor.modelo.EstadoProceso;
import gestor.modelo.EventoES;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import static gestor.pruebas.EscenariosMotor.*;

/** Escenarios 2 y 3 de la Historia 1 y conservación de memoria de la Historia 2. */
public class MotorEntradaSalidaPrueba {
    @Prueba
    public void reproduceElEscenarioDosConDosTicksDeCpuOciosa() {
        Motor motor = crear(100, 1, definir("P1", 0, 3, 10, 0, new EventoES(1, 2)));
        avanzar(motor, 1);
        Afirmar.iguales(EstadoProceso.BLOQUEADO, proceso(motor, "P1").estado(), "Se bloquea tras el tick 0");
        Afirmar.iguales(3, proceso(motor, "P1").finBloqueo(), "La E/S termina en el instante 3");
        Afirmar.iguales(0, proceso(motor, "P1").ticksES(), "La solicitud no consume un tick de E/S todavía");
        avanzar(motor, 2);
        Afirmar.iguales(1, proceso(motor, "P1").ticksConsumidos(), "Durante los ticks 1 y 2 no consume CPU");
        Afirmar.iguales(2, proceso(motor, "P1").ticksES(), "El bloqueo dura exactamente dos ticks");

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 1), new TramoGantt(null, 1, 3),
                new TramoGantt("P1", 3, 4), new TramoGantt("P1", 4, 5)), motor.gantt(),
                "Cada turno de quantum 1 conserva su tramo y los dos ticks ociosos se agrupan");
        Afirmar.iguales(5, proceso(motor, "P1").instanteFinalizacion(), "P1 termina en 5");
        Afirmar.iguales(5, proceso(motor, "P1").tiempoRetorno(), "Retorno de cinco ticks");
        Afirmar.iguales(0, proceso(motor, "P1").tiempoEspera(), "La E/S no cuenta como espera");
        Afirmar.verdadero(motor.bitacora().contains(new EventoBitacora(3, "P1", EstadoProceso.BLOQUEADO,
                EstadoProceso.LISTO, "Fin de E/S")), "Se registra el regreso en el instante 3");
    }

    @Prueba
    public void solicitaEntradaSalidaAntesDeAgotarUnQuantumDeTres() {
        Motor motor = crear(100, 3, definir("P1", 0, 3, 10, 0, new EventoES(1, 2)));

        avanzar(motor, 1);

        var proceso = proceso(motor, "P1");
        Afirmar.iguales(EstadoProceso.BLOQUEADO, proceso.estado(), "La E/S abandona la CPU inmediatamente");
        Afirmar.iguales(1, proceso.ticksQuantumUsados(), "Solo se consumió un tick del turno de tres");
        Afirmar.verdadero(proceso.tieneMemoria(), "El bloqueo conserva la asignación de RAM");
        Afirmar.verdadero(motor.bitacora().contains(new EventoBitacora(1, "P1", EstadoProceso.EJECUCION,
                EstadoProceso.BLOQUEADO, "Solicita E/S (2 ticks)")), "Motivo e instante de la solicitud exactos");
    }

    @Prueba
    public void entradaSalidaPrevaleceSiCoincideConElFinDeQuantum() {
        Motor motor = crear(100, 1, definir("P1", 0, 2, 10, 0, new EventoES(1, 1)));

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 1), new TramoGantt(null, 1, 2),
                new TramoGantt("P1", 2, 3)), motor.gantt(), "El proceso bloqueado no se reencola al agotar el quantum");
        Afirmar.falso(motor.bitacora().stream().anyMatch(evento -> evento.motivo().equals("Quantum agotado")),
                "Ni la solicitud de E/S ni el fin de ráfaga deben convertirse en expropiación");
    }

    @Prueba
    public void conservaElMismoBloqueDuranteTodaLaEntradaSalida() {
        Motor motor = crear(100, 3, definir("P1", 0, 3, 25, 0, new EventoES(1, 2)));
        avanzar(motor, 1);
        var bloque = motor.mapaMemoria().bloqueDe("P1");

        for (int tick = 1; tick <= 2; tick++) {
            Afirmar.iguales(EstadoProceso.BLOQUEADO, proceso(motor, "P1").estado(), "El proceso está bloqueado");
            Afirmar.iguales(bloque, motor.mapaMemoria().bloqueDe("P1"), "La E/S conserva el bloque completo");
            Afirmar.iguales(bloque.orElseThrow().base(), proceso(motor, "P1").baseBloque(), "La base del PCB se conserva");
            Afirmar.iguales(25, proceso(motor, "P1").tamanoBloque(), "El tamaño del PCB se conserva");
            avanzar(motor, 1);
        }
        avanzar(motor, 1);
        Afirmar.iguales(bloque, motor.mapaMemoria().bloqueDe("P1"), "El regreso de E/S tampoco reasigna el bloque");
        finalizar(motor);
        Afirmar.falso(proceso(motor, "P1").tieneMemoria(), "Solo se libera al terminar");
        Afirmar.verdadero(motor.mapaMemoria().bloqueDe("P1").isEmpty(), "El mapa también libera el bloque al terminar");
    }

    @Prueba
    public void dosProcesosCumplenEntradaSalidaEnParaleloYRegresanEnOrdenDeBloqueo() {
        Motor motor = crear(100, 1, definir("A", 0, 3, 10, 0, new EventoES(1, 3)),
                definir("B", 0, 3, 10, 1, new EventoES(1, 2)));
        avanzar(motor, 2);
        Afirmar.iguales(4, proceso(motor, "A").finBloqueo(), "A termina su E/S en 4");
        Afirmar.iguales(4, proceso(motor, "B").finBloqueo(), "B termina su E/S también en 4");
        avanzar(motor, 1);
        Afirmar.iguales(2, proceso(motor, "A").ticksES(), "A acumuló E/S en los ticks 1 y 2");
        Afirmar.iguales(1, proceso(motor, "B").ticksES(), "B también acumuló E/S en el tick 2");

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("A", 0, 1), new TramoGantt("B", 1, 2),
                new TramoGantt(null, 2, 4), new TramoGantt("A", 4, 5), new TramoGantt("B", 5, 6),
                new TramoGantt("A", 6, 7), new TramoGantt("B", 7, 8)), motor.gantt(),
                "Las E/S se solapan y no forman una cola de dispositivo");
        Afirmar.iguales(List.of("A", "B"), motor.bitacora().stream()
                .filter(evento -> evento.instante() == 4 && evento.motivo().equals("Fin de E/S"))
                .map(EventoBitacora::idProceso).toList(), "Los retornos simultáneos respetan el orden de bloqueo");
        Afirmar.iguales(3, proceso(motor, "A").ticksES(), "Duración total de E/S de A");
        Afirmar.iguales(2, proceso(motor, "B").ticksES(), "Duración total de E/S de B");
    }

    @Prueba
    public void aplicaVariosEventosEnOrdenSegunElConsumoAcumuladoDeCpu() {
        Motor motor = crear(100, 3, definir("P1", 0, 5, 10, 0, new EventoES(1, 2), new EventoES(3, 1)));

        finalizar(motor);

        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 1), new TramoGantt(null, 1, 3),
                new TramoGantt("P1", 3, 5), new TramoGantt(null, 5, 6), new TramoGantt("P1", 6, 8)),
                motor.gantt(), "Los eventos se activan por CPU acumulada, no por tick global ni turno");
        Afirmar.iguales(List.of(1, 5), motor.bitacora().stream()
                .filter(evento -> evento.destino() == EstadoProceso.BLOQUEADO).map(EventoBitacora::instante).toList(),
                "Las solicitudes ocurren tras los ticks de CPU 1 y 3");
        Afirmar.iguales(List.of(3, 6), motor.bitacora().stream()
                .filter(evento -> evento.motivo().equals("Fin de E/S")).map(EventoBitacora::instante).toList(),
                "Cada E/S tiene su propio instante de finalización");
        Afirmar.iguales(3, proceso(motor, "P1").ticksES(), "Se suman ambas duraciones de E/S");
        Afirmar.verdadero(proceso(motor, "P1").siguienteEventoES().isEmpty(), "Todos los eventos fueron atendidos");
    }

    @Prueba
    public void calculaFinBloqueoDesdeElFinDelTickDeLaSolicitud() {
        Motor motor = crear(100, 3, definir("P1", 3, 2, 10, 0, new EventoES(1, 4)));

        avanzar(motor, 4);

        Afirmar.iguales(8, proceso(motor, "P1").finBloqueo(), "El tick 3 termina en 4; cuatro ticks de E/S terminan en 8");
        finalizar(motor);
        Afirmar.iguales(List.of(new TramoGantt(null, 0, 3), new TramoGantt("P1", 3, 4),
                new TramoGantt(null, 4, 8), new TramoGantt("P1", 8, 9)), motor.gantt(),
                "No hay desfase de un tick en el bloqueo");
        Afirmar.iguales(4, proceso(motor, "P1").ticksES(), "La duración real coincide con la solicitada");
    }
}
