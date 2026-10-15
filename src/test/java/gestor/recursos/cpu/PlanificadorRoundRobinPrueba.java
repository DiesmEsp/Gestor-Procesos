package gestor.recursos.cpu;

import gestor.modelo.DefinicionProceso;
import gestor.modelo.EstadoProceso;
import gestor.modelo.Proceso;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;

/** Contrato FIFO y límites de quantum del planificador Round Robin (FR-008). */
public class PlanificadorRoundRobinPrueba {
    @Prueba
    public void extraeLosProcesosEnOrdenFifo() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        Proceso primero = crearProceso("P1");
        Proceso segundo = crearProceso("P2");
        Proceso tercero = crearProceso("P3");
        planificador.encolar(primero);
        planificador.encolar(segundo);
        planificador.encolar(tercero);

        Afirmar.iguales(List.of(primero, segundo, tercero), planificador.cola(), "Se conserva el orden de llegada");
        Afirmar.verdadero(planificador.siguiente() == primero, "Se extrae primero P1");
        Afirmar.iguales(List.of(segundo, tercero), planificador.cola(), "El proceso extraído deja de estar en cola");
        Afirmar.verdadero(planificador.siguiente() == segundo, "Se extrae después P2");
        Afirmar.verdadero(planificador.siguiente() == tercero, "Se extrae al final P3");
        Afirmar.iguales(List.of(), planificador.cola(), "Cada extracción elimina un único elemento");
    }

    @Prueba
    public void reencolaUnProcesoAlFinalDeLosQueSiguenEsperando() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        Proceso primero = crearProceso("P1");
        Proceso segundo = crearProceso("P2");
        Proceso tercero = crearProceso("P3");
        planificador.encolar(primero);
        planificador.encolar(segundo);
        Afirmar.verdadero(planificador.siguiente() == primero, "P1 recibe el primer turno");

        planificador.encolar(tercero);
        planificador.encolar(primero);

        Afirmar.iguales(List.of(segundo, tercero, primero), planificador.cola(),
                "El proceso reencolado espera detrás de los que ya estaban listos");
        Afirmar.verdadero(planificador.siguiente() == segundo, "P2 conserva su turno");
        Afirmar.verdadero(planificador.siguiente() == tercero, "El recién encolado P3 va antes del reencolado");
        Afirmar.verdadero(planificador.siguiente() == primero, "P1 vuelve a ejecutarse al final");
    }

    @Prueba
    public void devuelveNuloCuandoLaColaEstaVacia() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();

        Afirmar.nulo(planificador.siguiente(), "Una cola nueva no tiene siguiente proceso");
        planificador.encolar(crearProceso("P1"));
        planificador.siguiente();
        Afirmar.nulo(planificador.siguiente(), "Una cola agotada tampoco tiene siguiente proceso");
    }

    @Prueba
    public void noExpropiaAntesDeConsumirElQuantum() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        Proceso proceso = crearEnEjecucion();

        proceso.fijarTicksQuantumUsados(0);
        Afirmar.falso(planificador.debeExpropiar(proceso, 1), "Sin consumir CPU no se agota el quantum mínimo");
        proceso.fijarTicksQuantumUsados(2);
        Afirmar.falso(planificador.debeExpropiar(proceso, 3), "Con 2 de 3 ticks aún queda un tick de turno");
    }

    @Prueba
    public void expropiaJustoAlAlcanzarElQuantumConfigurado() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        Proceso proceso = crearEnEjecucion();

        proceso.fijarTicksQuantumUsados(1);
        Afirmar.verdadero(planificador.debeExpropiar(proceso, 1), "El quantum mínimo se agota tras un tick");
        proceso.fijarTicksQuantumUsados(3);
        Afirmar.verdadero(planificador.debeExpropiar(proceso, 3), "La igualdad con el quantum provoca expropiación");
        Afirmar.falso(planificador.debeExpropiar(proceso, 5), "Los mismos 3 ticks no agotan un quantum de 5");
    }

    @Prueba
    public void expropiaSiLosTicksUsadosSuperanElQuantum() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        Proceso proceso = crearEnEjecucion();
        proceso.fijarTicksQuantumUsados(4);

        Afirmar.verdadero(planificador.debeExpropiar(proceso, 3),
                "La condición de expropiación también incluye valores mayores al quantum");
    }

    @Prueba
    public void exponeLaColaEnOrdenSinPermitirModificacionesExternas() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        Proceso primero = crearProceso("P1");
        Proceso segundo = crearProceso("P2");
        Proceso ajeno = crearProceso("AJENO");
        planificador.encolar(primero);
        planificador.encolar(segundo);
        List<Proceso> cola = planificador.cola();

        Afirmar.iguales(List.of(primero, segundo), cola, "La consulta respeta el orden FIFO");
        Afirmar.lanza(UnsupportedOperationException.class, () -> cola.add(ajeno));
        Afirmar.lanza(UnsupportedOperationException.class, () -> cola.remove(0));
        Afirmar.lanza(UnsupportedOperationException.class, () -> cola.set(0, ajeno));
        Afirmar.lanza(UnsupportedOperationException.class, cola::clear);
        Afirmar.iguales(List.of(primero, segundo), planificador.cola(), "La cola interna debe conservarse intacta");
    }

    @Prueba
    public void vaciarRetiraTodosLosProcesosYPermiteVolverAEncolar() {
        PlanificadorRoundRobin planificador = new PlanificadorRoundRobin();
        planificador.encolar(crearProceso("P1"));
        planificador.encolar(crearProceso("P2"));

        planificador.vaciar();

        Afirmar.iguales(List.of(), planificador.cola(), "Vaciar retira todos los elementos");
        Afirmar.nulo(planificador.siguiente(), "Después de vaciar no existe siguiente proceso");
        Proceso nuevo = crearProceso("P3");
        planificador.encolar(nuevo);
        Afirmar.verdadero(planificador.siguiente() == nuevo, "El planificador sigue siendo utilizable");
    }

    @Prueba
    public void exponeElNombreRoundRobin() {
        Afirmar.iguales("Round Robin", new PlanificadorRoundRobin().nombre(),
                "El planificador debe identificarse como Round Robin");
    }

    private Proceso crearProceso(String identificador) {
        Proceso proceso = new Proceso(new DefinicionProceso(identificador, 0, 10, 10, null, List.of(), 0));
        proceso.cambiarEstado(EstadoProceso.NUEVO);
        proceso.cambiarEstado(EstadoProceso.LISTO);
        return proceso;
    }

    private Proceso crearEnEjecucion() {
        Proceso proceso = crearProceso("P1");
        proceso.cambiarEstado(EstadoProceso.EJECUCION);
        return proceso;
    }
}
