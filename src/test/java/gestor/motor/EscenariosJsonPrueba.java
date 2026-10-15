package gestor.motor;

import gestor.entrada.LectorEscenario;
import gestor.modelo.Escenario;
import gestor.modelo.EstadoProceso;
import gestor.pruebas.Afirmar;
import gestor.pruebas.InvariantesMemoria;
import gestor.pruebas.Prueba;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static gestor.pruebas.EscenariosMotor.*;

/** Los archivos de T044 también se verifican contra el motor y las referencias SC-001. */
public class EscenariosJsonPrueba {
    @Prueba
    public void referenciaRoundRobinCoincideConElGanttYLasMetricasDeLaSpec() {
        Motor motor = cargar("referencia-rr.json");
        finalizar(motor);
        Afirmar.iguales(List.of(new TramoGantt("P1", 0, 2), new TramoGantt("P2", 2, 4),
                new TramoGantt("P1", 4, 6), new TramoGantt("P2", 6, 7)), motor.gantt(), "Referencia RR desde archivo");
        Afirmar.iguales(new Metricas(2.5, 6.0, 100.0, 2, 2), motor.metricas(), "Métricas exactas de referencia");
    }

    @Prueba
    public void referenciaEntradaSalidaCoincideConLaSpec() {
        Motor motor = cargar("referencia-es.json");
        finalizar(motor);
        Afirmar.iguales(5, proceso(motor, "P1").instanteFinalizacion(), "La referencia de E/S termina en 5");
        Afirmar.iguales(new Metricas(0.0, 5.0, 60.0, 1, 1), motor.metricas(), "Métricas de E/S desde archivo");
    }

    @Prueba
    public void ejemplosDePoliticasProducenLasDireccionesEsperadas() {
        for (String nombre : List.of("best-fit.json", "first-fit.json")) {
            Motor motor = cargar(nombre);
            avanzar(motor, 4);
            Afirmar.iguales(nombre.equals("best-fit.json") ? 70 : 0, proceso(motor, "D").baseBloque(),
                    "El proceso de 25 MB recibe la base del escenario de referencia");
            finalizar(motor);
        }
    }

    @Prueba
    public void ejemploDeInanicionAlcanzaCincoYDetieneAlSiguienteProceso() {
        Motor motor = cargar("inanicion.json");
        avanzar(motor, 1);
        Afirmar.iguales(5, proceso(motor, "G").adelantamientos(), "G fue adelantado exactamente cinco veces");
        Afirmar.verdadero(motor.esBloqueante(proceso(motor, "G")), "G bloquea admisiones posteriores");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "S6").estado(), "S6 espera pese a caber");
        finalizar(motor);
    }

    @Prueba
    public void escenariosDidacticoYDeCargaSonValidosYCompletanTodaSuCpu() throws Exception {
        for (String nombre : List.of("config.json", "carga-50.json")) {
            Escenario escenario = new LectorEscenario().leer(Path.of("ejemplos", nombre));
            Afirmar.iguales(nombre.equals("config.json") ? 6 : 50, escenario.procesos().size(), "Cantidad de procesos solicitada");
            Afirmar.verdadero(escenario.procesos().stream().allMatch(definicion -> !definicion.bloqueosES().isEmpty()),
                    "Los procesos incluyen eventos de E/S");
            Motor motor = crearMotor(escenario);
            for (int tick = 0; tick < 10000 && !motor.finalizada(); tick++) {
                motor.avanzarTick();
                InvariantesMemoria.verificar(motor.mapaMemoria());
            }
            Afirmar.verdadero(motor.finalizada(), "La carga debe terminar sin inanición");
            Afirmar.iguales(escenario.procesos().stream().mapToInt(definicion -> definicion.tiempoRafaga()).sum(),
                    motor.ticksCpuOcupada(), "El consumo de CPU coincide con la suma de ráfagas");
            Afirmar.verdadero(motor.procesos().stream().anyMatch(proceso -> proceso.ticksEsperaMemoria() > 0),
                    "El escenario provoca espera de memoria");
        }
        Afirmar.iguales(Files.readString(Path.of("ejemplos/config.json")), Files.readString(Path.of("config.json")),
                "El escenario de la raíz es una copia exacta del didáctico");
    }

    private Motor cargar(String nombre) {
        return crearMotor(new LectorEscenario().leer(Path.of("ejemplos", nombre)));
    }

    private Motor crearMotor(Escenario escenario) {
        return new Motor(escenario, FabricaRecursos.crearPolitica(escenario.configuracion().politicaMemoria()),
                FabricaRecursos.crearPlanificador(escenario.configuracion().planificador()));
    }
}
