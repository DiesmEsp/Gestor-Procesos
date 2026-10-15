package gestor.motor;

import gestor.modelo.AlgoritmoPlanificacion;
import gestor.modelo.PoliticaMemoria;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import gestor.recursos.cpu.Planificador;
import gestor.recursos.cpu.PlanificadorRoundRobin;
import gestor.recursos.memoria.MejorAjuste;
import gestor.recursos.memoria.PoliticaAsignacion;
import gestor.recursos.memoria.PrimerAjuste;
import java.util.Optional;

/** Traducción de códigos de configuración a políticas y planificadores concretos. */
public class FabricaRecursosPrueba {
    @Prueba
    public void creaMejorAjusteParaBestFit() {
        PoliticaAsignacion politica = FabricaRecursos.crearPolitica(PoliticaMemoria.BEST_FIT);

        Afirmar.verdadero(politica instanceof MejorAjuste, "BEST_FIT debe crear MejorAjuste");
    }

    @Prueba
    public void creaPrimerAjusteParaFirstFit() {
        PoliticaAsignacion politica = FabricaRecursos.crearPolitica(PoliticaMemoria.FIRST_FIT);

        Afirmar.verdadero(politica instanceof PrimerAjuste, "FIRST_FIT debe crear PrimerAjuste");
    }

    @Prueba
    public void creaElPlanificadorRoundRobin() {
        Planificador planificador = FabricaRecursos.crearPlanificador(AlgoritmoPlanificacion.ROUND_ROBIN);

        Afirmar.verdadero(planificador instanceof PlanificadorRoundRobin,
                "ROUND_ROBIN debe crear PlanificadorRoundRobin");
    }

    @Prueba
    public void reconoceLosCodigosExactosDeLasPoliticas() {
        Afirmar.iguales(Optional.of(PoliticaMemoria.BEST_FIT), PoliticaMemoria.desdeCodigo("BEST_FIT"),
                "El código BEST_FIT corresponde a su constante");
        Afirmar.iguales(Optional.of(PoliticaMemoria.FIRST_FIT), PoliticaMemoria.desdeCodigo("FIRST_FIT"),
                "El código FIRST_FIT corresponde a su constante");
    }

    @Prueba
    public void reconoceElCodigoExactoDelPlanificador() {
        Afirmar.iguales(Optional.of(AlgoritmoPlanificacion.ROUND_ROBIN),
                AlgoritmoPlanificacion.desdeCodigo("ROUND_ROBIN"),
                "El código ROUND_ROBIN corresponde a su constante");
    }

    @Prueba
    public void devuelveVacioParaPoliticasDesconocidas() {
        for (String codigo : new String[] {"WORST_FIT", "Best-Fit", ""}) {
            Afirmar.iguales(Optional.empty(), PoliticaMemoria.desdeCodigo(codigo),
                    "Una política desconocida debe rechazarse: " + codigo);
        }
    }

    @Prueba
    public void devuelveVacioParaPlanificadoresDesconocidos() {
        for (String codigo : new String[] {"FCFS", "Round Robin", ""}) {
            Afirmar.iguales(Optional.empty(), AlgoritmoPlanificacion.desdeCodigo(codigo),
                    "Un planificador no implementado debe rechazarse: " + codigo);
        }
    }

    @Prueba
    public void losCodigosDePoliticaDistinguenMayusculasDeMinusculas() {
        for (String codigo : new String[] {"best_fit", "first_fit"}) {
            Afirmar.iguales(Optional.empty(), PoliticaMemoria.desdeCodigo(codigo),
                    "Las políticas exigen el nombre exacto en mayúsculas: " + codigo);
        }
    }

    @Prueba
    public void elCodigoDePlanificadorDistingueMayusculasDeMinusculas() {
        Afirmar.iguales(Optional.empty(), AlgoritmoPlanificacion.desdeCodigo("round_robin"),
                "El planificador exige el nombre exacto en mayúsculas");
    }
}
