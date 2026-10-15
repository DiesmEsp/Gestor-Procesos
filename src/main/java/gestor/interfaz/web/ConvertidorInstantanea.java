package gestor.interfaz.web;

import gestor.motor.Instantanea;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapta los registros del motor al contrato HTTP sin acoplar el motor a JSON. */
public final class ConvertidorInstantanea {
    private ConvertidorInstantanea() {}

    /**
     * Adapta una captura al contrato HTTP, incluyendo solo el sufijo solicitado de la bitácora.
     *
     * @param estado captura completa del controlador
     * @param desdeEvento índice del primer evento que se incluye; cero devuelve toda la bitácora
     * @return el objeto JSON del contrato como mapa de tipos estándar
     */
    public static Map<String, Object> aMapa(Instantanea estado, long desdeEvento) {
        var configuracion = estado.configuracion();
        var colas = estado.colas();
        var memoria = estado.memoria();
        var metricas = estado.metricas();
        int inicio = (int) Math.min(Math.max(0, desdeEvento), estado.bitacora().size());
        return mapa("instante", estado.instante(), "modo", estado.modo().name(),
                "configuracion", mapa("memoriaTotal", configuracion.memoriaTotal(), "quantum", configuracion.quantum(),
                        "maxAdelantamientos", configuracion.maxAdelantamientos(), "velocidadMs", estado.velocidadMs(),
                        "politicaMemoria", configuracion.politicaMemoria().name(), "planificador", configuracion.planificador().name()),
                "colas", mapa("nuevos", colas.nuevos().stream().map(proceso -> mapa("id", proceso.id(), "memoria", proceso.memoria(),
                        "adelantamientos", proceso.adelantamientos(), "bloqueante", proceso.bloqueante())).toList(),
                        "listos", colas.listos(), "ejecucion", colas.ejecucion() == null ? null : mapa("id", colas.ejecucion().id(),
                                "ticksQuantumUsados", colas.ejecucion().ticksQuantumUsados()),
                        "bloqueados", colas.bloqueados().stream().map(proceso -> mapa("id", proceso.id(), "ticksRestantesES", proceso.ticksRestantesES())).toList(),
                        "terminados", colas.terminados()),
                "procesos", estado.procesos().stream().map(ConvertidorInstantanea::proceso).toList(),
                "memoria", mapa("total", memoria.total(), "libre", memoria.libre(), "mayorHueco", memoria.mayorHueco(),
                        "fragmentacionExterna", memoria.fragmentacionExterna(), "bloques", memoria.bloques().stream().map(bloque ->
                                mapa("base", bloque.base(), "limite", bloque.limite(), "tamano", bloque.tamano(), "idProceso", bloque.idProceso())).toList()),
                "gantt", estado.gantt().stream().map(tramo -> mapa("idProceso", tramo.idProceso(), "inicio", tramo.inicio(), "fin", tramo.fin())).toList(),
                "bitacora", estado.bitacora().subList(inicio, estado.bitacora().size()).stream().map(evento -> mapa("instante", evento.instante(),
                        "idProceso", evento.idProceso(), "origen", evento.origen().name(), "destino", evento.destino().name(), "motivo", evento.motivo())).toList(),
                "totalEventos", estado.bitacora().size(), "metricas", mapa("esperaPromedio", metricas.esperaPromedio(),
                        "retornoPromedio", metricas.retornoPromedio(), "utilizacionCpu", metricas.utilizacionCpu(),
                        "terminados", metricas.terminados(), "total", metricas.total()), "aviso", estado.aviso());
    }

    /**
     * Adapta un resumen de proceso e incorpora el instante de la consulta.
     *
     * @param proceso proceso consultado o incorporado a la cola
     * @param instante instante simulado en ticks
     * @return el mapa de campos del proceso más el instante
     */
    public static Map<String, Object> proceso(Instantanea.ResumenProceso proceso, int instante) {
        var resultado = proceso(proceso);
        resultado.put("instante", instante);
        return resultado;
    }

    private static Map<String, Object> proceso(Instantanea.ResumenProceso proceso) {
        var bloque = proceso.bloque();
        return mapa("id", proceso.id(), "estado", proceso.estado().name(), "tiempoLlegada", proceso.tiempoLlegada(),
                "tiempoRafaga", proceso.tiempoRafaga(), "ticksConsumidos", proceso.ticksConsumidos(),
                "ticksRestantes", proceso.ticksRestantes(), "memoria", proceso.memoria(), "prioridad", proceso.prioridad(),
                "bloque", bloque == null ? null : mapa("base", bloque.base(), "limite", bloque.limite(), "tamano", bloque.tamano()),
                "tiempoEspera", proceso.tiempoEspera(), "ticksES", proceso.ticksES(), "adelantamientos", proceso.adelantamientos(),
                "bloqueante", proceso.bloqueante(), "ticksRestantesES", proceso.ticksRestantesES(),
                "instanteFinalizacion", proceso.instanteFinalizacion(), "tiempoRetorno", proceso.tiempoRetorno());
    }

    /** LinkedHashMap conserva el orden del contrato y permite valores nulos. */
    private static Map<String, Object> mapa(Object... pares) {
        var resultado = new LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) resultado.put((String) pares[indice], pares[indice + 1]);
        return resultado;
    }
}
