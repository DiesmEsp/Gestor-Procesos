package gestor.motor;

import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.DefinicionProceso;
import gestor.modelo.Escenario;
import gestor.modelo.EstadoProceso;
import gestor.modelo.EventoES;
import gestor.modelo.PoliticaMemoria;
import gestor.modelo.Proceso;
import gestor.pruebas.Afirmar;
import gestor.pruebas.InvariantesMemoria;
import gestor.pruebas.Prueba;
import gestor.recursos.memoria.Bloque;
import java.util.ArrayList;
import java.util.List;
import static gestor.pruebas.EscenariosMotor.*;

/** Escenarios de la Historia 2, admisión FR-015a e invariantes SC-007. */
public class MotorAdmisionMemoriaPrueba {
    @Prueba
    public void detectaSetentaMbLibresSinUnHuecoContiguoDeSesenta() {
        Motor motor = crear(100, 1, definir("A", 0, 1, 30, 0), definir("B", 0, 10, 30, 1),
                definir("C", 0, 1, 40, 2), definir("D", 3, 1, 60, 3));
        avanzar(motor, 3);
        Afirmar.iguales(List.of(new Bloque(0, 30, null), new Bloque(30, 30, "B"),
                new Bloque(60, 40, null)), motor.mapaMemoria().bloques(), "Disposición previa del escenario 4");

        avanzar(motor, 1);

        Afirmar.iguales(70, motor.mapaMemoria().memoriaLibre(), "Hay 70 MB libres en total");
        Afirmar.iguales(40, motor.mapaMemoria().mayorHueco(), "El mayor hueco mide 40 MB");
        Afirmar.iguales(30, motor.mapaMemoria().fragmentacionExterna(), "Fragmentación externa: 70 menos 40");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "D").estado(), "D espera memoria contigua");
        Afirmar.falso(proceso(motor, "D").tieneMemoria(), "No se asigna memoria parcialmente");
        Afirmar.verdadero(motor.bitacora().stream().anyMatch(evento -> evento.idProceso().equals("D")
                && evento.motivo().equals("Sin memoria contigua suficiente")), "Se registra la causa de la espera");
        finalizar(motor);
    }

    @Prueba
    public void admitePendientesEnElInstanteDeLiberacionAntesDeLasNuevasLlegadas() {
        Motor motor = crear(100, 2, definir("A", 0, 2, 100, 0), definir("B", 0, 1, 100, 1),
                definir("C", 2, 1, 100, 2));
        avanzar(motor, 2);
        Afirmar.iguales(2, proceso(motor, "A").instanteFinalizacion(), "A libera la RAM en el instante 2");

        avanzar(motor, 1);

        EventoBitacora admision = admision(motor, "B");
        EventoBitacora llegada = motor.bitacora().stream().filter(evento -> evento.idProceso().equals("C")
                && evento.motivo().equals("Llegada")).findFirst().orElseThrow();
        Afirmar.iguales(2, admision.instante(), "B se admite en el mismo instante en que A libera memoria");
        Afirmar.verdadero(motor.bitacora().indexOf(admision) < motor.bitacora().indexOf(llegada),
                "El paso 1 admite al pendiente antes de procesar la llegada de C");
        Afirmar.iguales(2, proceso(motor, "B").ticksEsperaMemoria(), "No se añade un tick extra de espera");
        Afirmar.iguales(2, proceso(motor, "B").tiempoEspera(), "La espera total incluye la RAM");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "C").estado(), "C se coloca después de B");
        finalizar(motor);
        Afirmar.iguales(List.of(new TramoGantt("A", 0, 2), new TramoGantt("B", 2, 3),
                new TramoGantt("C", 3, 4)), motor.gantt(), "La liberación no introduce un tick ocioso");
    }

    @Prueba
    public void cadaRecienLlegadoAdmitidoAdelantaATodosLosAnterioresQueSiguenEsperando() {
        Motor motor = crear(100, 1, definir("O", 0, 20, 30, 0), definir("A", 0, 1, 80, 1),
                definir("B", 0, 1, 90, 2), definir("S1", 0, 1, 10, 3),
                definir("S2", 0, 1, 10, 4), definir("S3", 0, 1, 10, 5));

        avanzar(motor, 1);

        Afirmar.iguales(3, proceso(motor, "A").adelantamientos(), "A fue adelantado por S1, S2 y S3");
        Afirmar.iguales(3, proceso(motor, "B").adelantamientos(), "B también fue adelantado tres veces");
        Afirmar.iguales(0, proceso(motor, "O").adelantamientos(), "No cuenta la admisión de un proceso anterior");
        Afirmar.iguales(0, proceso(motor, "S1").adelantamientos(), "Un admitido deja de acumular adelantamientos");
        avanzar(motor, 1);
        Afirmar.iguales(3, proceso(motor, "A").adelantamientos(), "Los reintentos y despachos no suman adelantamientos");
        Afirmar.iguales(3, proceso(motor, "B").adelantamientos(), "Solo se cuentan nuevas admisiones posteriores");
        finalizar(motor);
    }

    @Prueba
    public void unaAdmisionDesdeLaColaDeEsperaTambienCuentaComoAdelantamiento() {
        Motor motor = crear(100, 1, definir("O", 0, 10, 40, 0, new EventoES(1, 5)), definir("X", 0, 1, 20, 1),
                definir("A", 0, 1, 80, 2), definir("B", 0, 1, 50, 3));
        avanzar(motor, 2);
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "B").estado(), "B esperaba cuando el mayor hueco era de 40 MB");
        Afirmar.iguales(60, motor.mapaMemoria().mayorHueco(), "X libera el bloque adyacente al hueco final");
        Afirmar.iguales(0, proceso(motor, "A").adelantamientos(), "Todavía no se admitió ningún candidato posterior");

        avanzar(motor, 1);

        Afirmar.iguales(2, admision(motor, "B").instante(), "La liberación de X permite admitir al pendiente B");
        Afirmar.iguales(1, proceso(motor, "A").adelantamientos(), "B adelanta al candidato A que sigue sin caber");
        Afirmar.iguales(0, proceso(motor, "B").adelantamientos(), "B no se cuenta a sí mismo");
        finalizar(motor);
    }

    @Prueba
    public void elQuintoAdelantamientoBloqueaLosPosterioresAunqueQuepanYRespetaLaColaHastaAdmitirG() {
        ConfiguracionSimulacion configuracion = ConfiguracionSimulacion.porDefecto().conMemoriaTotal(100);
        Afirmar.iguales(5, configuracion.maxAdelantamientos(), "El límite por defecto es cinco");
        Motor motor = crearConConfiguracion(configuracion, candidatosAntiInanicion());

        avanzar(motor, 1);

        Afirmar.iguales(5, proceso(motor, "G").adelantamientos(), "Se permiten exactamente cinco adelantamientos");
        Afirmar.verdadero(motor.esBloqueante(proceso(motor, "G")), "G pasa a ser bloqueante en ese mismo recorrido");
        Afirmar.verdadero(proceso(motor, "S5").tieneMemoria(), "La quinta admisión sí se conserva");
        Afirmar.iguales(20, motor.mapaMemoria().memoriaLibre(), "Queda memoria para S6 y S7");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "S6").estado(), "S6 ya no puede adelantar a G");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "S7").estado(), "S7 tampoco se admite en ese recorrido");
        avanzar(motor, 1);
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "N").estado(), "El recién llegado N se añade al final");
        Afirmar.falso(proceso(motor, "N").tieneMemoria(), "Una nueva llegada tampoco evita al bloqueante");
        Afirmar.verdadero(motor.bitacora().stream().anyMatch(evento -> evento.idProceso().equals("N")
                && evento.motivo().contains("bloqueante")), "La bitácora distingue la espera por un bloqueante");
        Afirmar.iguales(5, proceso(motor, "G").adelantamientos(), "Las admisiones prohibidas no suman al contador");

        avanzar(motor, 33);
        Afirmar.iguales(35, proceso(motor, "O").instanteFinalizacion(), "O libera la memoria que necesita G");
        Afirmar.verdadero(motor.esBloqueante(proceso(motor, "G")), "G sigue esperando hasta el paso 1 del instante 35");
        avanzar(motor, 1);

        Afirmar.iguales(List.of("G", "S6", "S7"), motor.bitacora().stream()
                .filter(evento -> evento.instante() == 35 && evento.motivo().startsWith("Memoria asignada"))
                .map(EventoBitacora::idProceso).toList(), "Al entrar G, los pendientes anteriores a N conservan su orden");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "N").estado(), "N queda detrás de S6 y S7");
        Afirmar.falso(motor.esBloqueante(proceso(motor, "G")), "Un proceso admitido deja de ser bloqueante");
        finalizar(motor);
        Afirmar.iguales(36, admision(motor, "N").instante(), "N se admite tras los pendientes más antiguos");
        Afirmar.iguales(35, proceso(motor, "S6").ticksEsperaMemoria(), "Toda la espera por el bloqueante cuenta como espera de RAM");
        Afirmar.iguales(1, proceso(motor, "S6").ticksEsperaListo(), "S6 espera un tick en Listo detrás de G");
        Afirmar.iguales(36, proceso(motor, "S6").tiempoEspera(), "La espera suma memoria y Listo");
        Afirmar.iguales(37, proceso(motor, "S6").tiempoRetorno(), "Retorno = un tick de ráfaga + 36 ticks de espera");
    }

    @Prueba
    public void elLimiteConfiguradoEnDosSeAplicaSinUsarElValorPorDefecto() {
        var configuracion = new ConfiguracionSimulacion(100, 1, 2,
                PoliticaMemoria.BEST_FIT, ConfiguracionSimulacion.PLANIFICADOR_POR_DEFECTO);
        Motor motor = crearConConfiguracion(configuracion,
                definir("O", 0, 10, 30, 0), definir("G", 0, 1, 80, 1),
                definir("S1", 0, 1, 10, 2), definir("S2", 0, 1, 10, 3), definir("S3", 0, 1, 10, 4));

        avanzar(motor, 1);

        Afirmar.iguales(2, proceso(motor, "G").adelantamientos(), "G alcanza el máximo configurado en dos");
        Afirmar.verdadero(motor.esBloqueante(proceso(motor, "G")), "El umbral usa la configuración del escenario");
        Afirmar.verdadero(proceso(motor, "S2").tieneMemoria(), "La segunda admisión es válida");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "S3").estado(), "La tercera se bloquea aunque quepa");
        finalizar(motor);
    }

    @Prueba
    public void unBloqueanteNoImpideAdmitirAProcesosAnterioresQueAhoraCaben() {
        var candidatos = new ArrayList<DefinicionProceso>();
        candidatos.add(definir("O", 0, 20, 20, 0, new EventoES(1, 10)));
        candidatos.add(definir("X", 0, 1, 10, 1));
        candidatos.add(definir("ANTERIOR", 0, 2, 80, 2));
        candidatos.add(definir("G", 0, 1, 90, 3));
        for (int indice = 1; indice <= 5; indice++) {
            candidatos.add(definir("S" + indice, 0, 1, 10, indice + 3));
        }
        Motor motor = crear(100, 1, candidatos.toArray(DefinicionProceso[]::new));
        avanzar(motor, 1);
        Afirmar.iguales(5, proceso(motor, "ANTERIOR").adelantamientos(), "Se incrementan todos los anteriores en espera");
        Afirmar.iguales(5, proceso(motor, "G").adelantamientos(), "G también alcanzó cinco");

        avanzar(motor, 7);

        Afirmar.iguales(7, admision(motor, "ANTERIOR").instante(), "ANTERIOR se admite cuando quedan 80 MB libres");
        Afirmar.verdadero(proceso(motor, "ANTERIOR").tieneMemoria(), "La protección de G no impide esta admisión anterior");
        Afirmar.iguales(EstadoProceso.NUEVO, proceso(motor, "G").estado(), "G aún necesita 90 MB");
        Afirmar.verdadero(motor.esBloqueante(proceso(motor, "G")), "G sigue bloqueando a sus posteriores");
        Afirmar.iguales(5, proceso(motor, "G").adelantamientos(), "Admitir un anterior no incrementa a G");
        finalizar(motor);
    }

    @Prueba
    public void esBloqueanteExigeEstadoNuevoYContadorMayorOIgualAlLimite() {
        Motor motor = crear(100, 1, definir("O", 0, 1, 10, 0));
        Proceso candidato = new Proceso(definir("G", 0, 1, 80, 1));
        candidato.fijarAdelantamientos(5);
        Afirmar.falso(motor.esBloqueante(candidato), "Un pendiente de llegada no es bloqueante");
        candidato.cambiarEstado(EstadoProceso.NUEVO);
        candidato.fijarAdelantamientos(4);
        Afirmar.falso(motor.esBloqueante(candidato), "Cuatro adelantamientos aún no alcanzan el máximo");
        candidato.fijarAdelantamientos(5);
        Afirmar.verdadero(motor.esBloqueante(candidato), "La igualdad con el umbral activa la protección");
        candidato.fijarAdelantamientos(6);
        Afirmar.verdadero(motor.esBloqueante(candidato), "Un valor superior también activa la protección");
        candidato.cambiarEstado(EstadoProceso.LISTO);
        Afirmar.falso(motor.esBloqueante(candidato), "Un admitido no bloquea aunque conserve el contador");
    }

    @Prueba
    public void verificaInvariantesTrasCadaTickDeUnEscenarioConOchoProcesos() {
        Motor motor = crear(100, 2, definir("A", 0, 4, 40, 0, new EventoES(1, 2)),
                definir("B", 0, 3, 35, 1), definir("C", 0, 2, 30, 2), definir("D", 1, 1, 25, 3),
                definir("E", 2, 3, 50, 4, new EventoES(1, 1)), definir("F", 3, 2, 15, 5),
                definir("G", 4, 1, 70, 6), definir("H", 5, 2, 20, 7));
        InvariantesMemoria.verificar(motor.mapaMemoria());

        for (int tick = 0; tick < 200 && !motor.finalizada(); tick++) {
            motor.avanzarTick();
            InvariantesMemoria.verificar(motor.mapaMemoria());
        }

        Afirmar.verdadero(motor.finalizada(), "La protección no impide que los ocho procesos terminen");
        Afirmar.iguales(18, motor.ticksCpuOcupada(), "Se consumieron todas las ráfagas, sin perder ticks");
        for (Proceso proceso : motor.procesos()) {
            Afirmar.iguales(EstadoProceso.TERMINADO, proceso.estado(), "Todos los procesos terminan");
            Afirmar.falso(proceso.tieneMemoria(), "Un proceso terminado no conserva RAM");
            Afirmar.iguales(proceso.definicion().tiempoRafaga() + proceso.ticksES() + proceso.tiempoEspera(),
                    proceso.tiempoRetorno(), "La invariante temporal también se conserva con anti-inanición");
        }
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), motor.mapaMemoria().bloques(),
                "La memoria final está completamente liberada y fusionada");
    }

    @Prueba
    public void comparaBestFitYFirstFitConLosHuecosDeLosEscenariosUnoYOcho() {
        for (PoliticaMemoria politica : PoliticaMemoria.values()) {
            Motor motor = crearConConfiguracion(ConfiguracionSimulacion.porDefecto().conMemoriaTotal(100)
                    .conPoliticaMemoria(politica), definir("A", 0, 1, 50, 0), definir("B", 0, 10, 20, 1),
                    definir("C", 0, 1, 20, 2), definir("D", 3, 1, 25, 3));
            avanzar(motor, 3);
            Afirmar.iguales(List.of(new Bloque(0, 50, null), new Bloque(50, 20, "B"), new Bloque(70, 30, null)),
                    motor.mapaMemoria().bloques(), "Ambas ejecuciones parten de los huecos [0–49] y [70–99]");

            avanzar(motor, 1);

            List<Bloque> esperados = politica == PoliticaMemoria.BEST_FIT
                    ? List.of(new Bloque(0, 50, null), new Bloque(50, 20, "B"), new Bloque(70, 25, "D"),
                            new Bloque(95, 5, null))
                    : List.of(new Bloque(0, 25, "D"), new Bloque(25, 25, null), new Bloque(50, 20, "B"),
                            new Bloque(70, 30, null));
            Afirmar.iguales(esperados, motor.mapaMemoria().bloques(),
                    "La fábrica debe componer la política " + politica + " sin cambiar el motor");
            Afirmar.iguales(politica == PoliticaMemoria.BEST_FIT ? 70 : 0, proceso(motor, "D").baseBloque(),
                    "Best-Fit asigna [70–94] y First-Fit asigna [0–24]");
            finalizar(motor);
        }
    }

    @Prueba
    public void bestFitDesempataPorMenorBaseEnLaAdmisionDelMotor() {
        Motor motor = crear(100, 1, definir("A", 0, 1, 30, 0), definir("B", 0, 10, 20, 1),
                definir("C", 0, 1, 30, 2), definir("D", 0, 10, 20, 3), definir("P", 3, 1, 25, 4));
        avanzar(motor, 3);
        Afirmar.iguales(List.of(new Bloque(0, 30, null), new Bloque(30, 20, "B"),
                new Bloque(50, 30, null), new Bloque(80, 20, "D")), motor.mapaMemoria().bloques(),
                "El escenario 2 tiene dos huecos iguales de 30 MB");

        avanzar(motor, 1);

        Afirmar.iguales(new Bloque(0, 25, "P"), motor.mapaMemoria().bloqueDe("P").orElseThrow(),
                "La admisión desempata por la base menor, 0");
        finalizar(motor);
    }

    @Prueba
    public void fusionaLosHuecosDeAYCLuegoConElBloqueDeBAlTerminar() {
        Motor motor = crear(100, 1, definir("A", 0, 1, 50, 0), definir("B", 0, 2, 20, 1), definir("C", 0, 1, 20, 2));

        avanzar(motor, 3);

        Afirmar.iguales(List.of(new Bloque(0, 50, null), new Bloque(50, 20, "B"), new Bloque(70, 30, null)),
                motor.mapaMemoria().bloques(), "Al terminar A y C quedan los huecos del escenario 3");
        finalizar(motor);
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), motor.mapaMemoria().bloques(),
                "Al terminar B se fusionan los huecos anterior y posterior");
    }

    private Motor crearConConfiguracion(ConfiguracionSimulacion configuracion, DefinicionProceso... candidatos) {
        return new Motor(new Escenario(configuracion, List.of(candidatos)),
                FabricaRecursos.crearPolitica(configuracion.politicaMemoria()),
                FabricaRecursos.crearPlanificador(configuracion.planificador()));
    }

    private DefinicionProceso[] candidatosAntiInanicion() {
        var candidatos = new ArrayList<DefinicionProceso>();
        candidatos.add(definir("O", 0, 30, 30, 0));
        candidatos.add(definir("G", 0, 1, 80, 1));
        for (int indice = 1; indice <= 7; indice++) {
            candidatos.add(definir("S" + indice, 0, 1, 10, indice + 1));
        }
        candidatos.add(definir("N", 1, 1, 10, 9));
        return candidatos.toArray(DefinicionProceso[]::new);
    }

    private EventoBitacora admision(Motor motor, String id) {
        return motor.bitacora().stream().filter(evento -> evento.idProceso().equals(id)
                && evento.motivo().startsWith("Memoria asignada")).findFirst()
                .orElseThrow(() -> new AssertionError("No se admitió el proceso " + id));
    }
}
