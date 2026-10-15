package gestor.motor;

import gestor.modelo.EventoES;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import static gestor.pruebas.EscenariosMotor.*;

/** Fórmulas FR-027 a FR-030 y escenarios de referencia calculados a mano. */
public class MetricasPrueba {
    @Prueba
    public void reproduceLasMetricasDelEscenarioUno() {
        Motor motor = crear(100, 2, definir("P1", 0, 4, 30, 0), definir("P2", 1, 3, 20, 1));

        finalizar(motor);

        Afirmar.iguales(new Metricas(2.5, 6.0, 100.0, 2, 2), motor.metricas(),
                "Espera promedio 2,5; retorno promedio 6; CPU 100 %");
        Afirmar.iguales(motor.metricas(), Metricas.calcular(motor.procesos(), 7, 7),
                "El motor delega el cálculo en la fábrica de métricas");
    }

    @Prueba
    public void reproduceLaUtilizacionDelEscenarioDosConEntradaSalida() {
        Motor motor = crear(100, 1, definir("P1", 0, 3, 10, 0, new EventoES(1, 2)));

        finalizar(motor);

        Afirmar.iguales(new Metricas(0.0, 5.0, 60.0, 1, 1), motor.metricas(),
                "Tres ticks de CPU de un total de cinco representan el 60 %");
        Afirmar.iguales(5, proceso(motor, "P1").tiempoRetorno(), "FR-027: finalización 5 menos llegada 0");
        Afirmar.iguales(0, proceso(motor, "P1").tiempoEspera(), "FR-028: 5 de retorno menos 3 de CPU menos 2 de E/S");
    }

    @Prueba
    public void incluyeLosTicksDeEsperaDeMemoriaEnElTiempoDeEspera() {
        Motor motor = crear(100, 2, definir("A", 0, 2, 100, 0), definir("B", 0, 1, 100, 1));
        avanzar(motor, 1);
        Afirmar.verdadero(motor.bitacora().stream().anyMatch(evento -> evento.idProceso().equals("B")
                && evento.motivo().equals("Sin memoria contigua suficiente")), "Se explica la espera de memoria");

        finalizar(motor);

        Afirmar.iguales(2, proceso(motor, "B").ticksEsperaMemoria(), "B espera memoria durante los ticks 0 y 1");
        Afirmar.iguales(0, proceso(motor, "B").ticksEsperaListo(), "B se despacha al admitirlo en el instante 2");
        Afirmar.iguales(2, proceso(motor, "B").tiempoEspera(), "La espera incluye memoria");
        Afirmar.iguales(3, proceso(motor, "B").tiempoRetorno(), "Retorno de B");
        Afirmar.iguales(new Metricas(1.0, 2.5, 100.0, 2, 2), motor.metricas(), "Promedios incluyendo espera de RAM");
    }

    @Prueba
    public void noCalculaPromediosAntesDeQueTermineAlgunProceso() {
        Motor motor = crear(100, 2, definir("P1", 0, 3, 10, 0));
        Afirmar.iguales(new Metricas(null, null, null, 0, 1), motor.metricas(),
                "En el instante 0 no hay promedios ni utilización");

        avanzar(motor, 1);

        Afirmar.iguales(new Metricas(null, null, 100.0, 0, 1), motor.metricas(),
                "La CPU ya tiene utilización aunque aún no haya procesos terminados");
    }

    @Prueba
    public void promediaSoloLosProcesosTerminados() {
        Motor motor = crear(100, 2, definir("A", 0, 1, 10, 0), definir("B", 0, 5, 10, 1));

        avanzar(motor, 1);

        Afirmar.iguales(new Metricas(0.0, 1.0, 100.0, 1, 2), motor.metricas(),
                "El proceso B sin terminar se excluye del denominador de los promedios");
        finalizar(motor);
        Afirmar.iguales(new Metricas(0.5, 3.5, 100.0, 2, 2), motor.metricas(),
                "Al terminar B, ambos procesos participan de los promedios");
    }

    @Prueba
    public void incluyeLosTicksOciososInicialesEnLaUtilizacion() {
        Motor motor = crear(100, 1, definir("P1", 3, 1, 10, 0));
        avanzar(motor, 1);
        Afirmar.iguales(new Metricas(null, null, 0.0, 0, 1), motor.metricas(), "Un tick ocioso tiene utilización cero");

        finalizar(motor);

        Afirmar.iguales(new Metricas(0.0, 1.0, 25.0, 1, 1), motor.metricas(),
                "Un tick ocupado de cuatro ticks totales representa el 25 %");
    }

    @Prueba
    public void conservaLaPrecisionSinRedondearLaUtilizacion() {
        Motor motor = crear(100, 1, definir("P1", 2, 1, 10, 0));

        finalizar(motor);

        Afirmar.iguales(100.0 / 3, motor.metricas().utilizacionCpu(), 1e-10,
                "El redondeo corresponde a la presentación, no al cálculo");
    }

    @Prueba
    public void cumpleLaInvarianteDeRetornoConEsperaEnListoMemoriaYEntradaSalida() {
        Motor motor = crear(100, 2, definir("A", 0, 5, 60, 0, new EventoES(2, 2)),
                definir("B", 0, 3, 60, 1), definir("C", 2, 2, 40, 2, new EventoES(1, 1)));

        finalizar(motor);

        for (var proceso : motor.procesos()) {
            int retornoEsperado = proceso.definicion().tiempoRafaga() + proceso.ticksES() + proceso.tiempoEspera();
            Afirmar.iguales(retornoEsperado, proceso.tiempoRetorno(),
                    "Retorno = ráfaga + E/S + espera para " + proceso.definicion().id());
            Afirmar.iguales(proceso.instanteFinalizacion() - proceso.definicion().tiempoLlegada(),
                    proceso.tiempoRetorno(), "El retorno usa la llegada de cada proceso");
        }
        Afirmar.iguales(8, proceso(motor, "B").ticksEsperaMemoria(), "B espera RAM hasta el instante 8");
        Afirmar.iguales(2, proceso(motor, "C").ticksEsperaListo(), "C también espera en Listo");
        Afirmar.iguales(2, proceso(motor, "A").ticksES(), "La E/S de A se contabiliza aparte");
        Afirmar.iguales(11.0 / 3, motor.metricas().esperaPromedio(), 1e-10, "El promedio de espera conserva precisión");
        Afirmar.iguales(8.0, motor.metricas().retornoPromedio(), 1e-10, "Retorno promedio del escenario mixto");
    }

    @Prueba
    public void unaListaSinProcesosProducePromediosNulos() {
        Afirmar.iguales(new Metricas(null, null, null, 0, 0), Metricas.calcular(List.of(), 0, 0),
                "El cálculo no divide entre cero cuando no hay procesos");
        Afirmar.iguales(new Metricas(null, null, 0.0, 0, 0), Metricas.calcular(List.of(), 0, 5),
                "Con ticks totales positivos, la CPU sin trabajo tiene utilización cero");
    }
}
