package gestor.pruebas;

import gestor.modelo.AlgoritmoPlanificacion;
import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.DefinicionProceso;
import gestor.modelo.Escenario;
import gestor.modelo.EventoES;
import gestor.modelo.PoliticaMemoria;
import gestor.modelo.Proceso;
import gestor.motor.FabricaRecursos;
import gestor.motor.Motor;
import java.util.List;

/** Construye y ejecuta escenarios deterministas sin archivos ni reloj de pared. */
public final class EscenariosMotor {
    private EscenariosMotor() {
    }

    public static DefinicionProceso definir(String id, int llegada, int rafaga, int memoria,
            int ordenArchivo, EventoES... eventos) {
        return new DefinicionProceso(id, llegada, rafaga, memoria, null, List.of(eventos), ordenArchivo);
    }

    public static Motor crear(int memoria, int quantum, DefinicionProceso... definiciones) {
        var configuracion = new ConfiguracionSimulacion(memoria, quantum, 5,
                PoliticaMemoria.BEST_FIT, AlgoritmoPlanificacion.ROUND_ROBIN);
        return new Motor(new Escenario(configuracion, List.of(definiciones)),
                FabricaRecursos.crearPolitica(configuracion.politicaMemoria()),
                FabricaRecursos.crearPlanificador(configuracion.planificador()));
    }

    public static void avanzar(Motor motor, int ticks) {
        for (int indice = 0; indice < ticks; indice++) {
            motor.avanzarTick();
            InvariantesMemoria.verificar(motor.mapaMemoria());
        }
    }

    public static void finalizar(Motor motor) {
        int limite = 1000;
        while (!motor.finalizada() && limite-- > 0) {
            avanzar(motor, 1);
        }
        Afirmar.verdadero(motor.finalizada(), "El escenario debe finalizar antes de 1000 ticks");
    }

    public static Proceso proceso(Motor motor, String id) {
        return motor.procesos().stream().filter(proceso -> proceso.definicion().id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("No existe el proceso " + id + " en el escenario"));
    }
}
