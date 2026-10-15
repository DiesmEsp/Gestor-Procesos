package gestor.motor;

import gestor.modelo.EstadoProceso;
import gestor.modelo.Proceso;
import java.util.List;

/**
 * Métricas sin redondeo; solo la presentación redondea los resultados.
 *
 * <ul>
 * <li>FR-027: Tiempo de retorno = tiempo de finalización − tiempo de llegada.</li>
 * <li>FR-028: Tiempo de espera = tiempo de retorno − ráfaga de CPU − tiempo total en E/S.
 * Equivale a ticks en Listo + ticks en Espera de Memoria.</li>
 * <li>FR-029: % de uso de CPU = ticks con CPU ocupada / ticks totales × 100.
 * Los ticks totales son el instante actual; al finalizar, el del último proceso.</li>
 * <li>FR-030: Promedios de espera y retorno = suma de los tiempos / procesos terminados.</li>
 * </ul>
 * Los promedios son nulos sin procesos terminados y la utilización es nula en el instante 0.
 *
 * @param esperaPromedio media de espera de los terminados; nula si no hay ninguno
 * @param retornoPromedio media de retorno de los terminados; nula si no hay ninguno
 * @param utilizacionCpu porcentaje de CPU ocupada; nulo en el instante cero
 * @param terminados cantidad de procesos terminados
 * @param total cantidad total de procesos
 */
public record Metricas(Double esperaPromedio, Double retornoPromedio, Double utilizacionCpu,
        int terminados, int total) {
    /**
     * Calcula promedios solo sobre TERMINADO y la utilización sobre todo el tiempo transcurrido.
     *
     * @param procesos procesos en orden de archivo
     * @param ticksCpuOcupada ticks con CPU ocupada
     * @param instanteActual ticks totales transcurridos
     * @return las métricas sin redondear
     */
    public static Metricas calcular(List<Proceso> procesos, int ticksCpuOcupada, int instanteActual) {
        long sumaEspera = 0;
        long sumaRetorno = 0;
        int terminados = 0;
        for (Proceso proceso : procesos) {
            if (proceso.estado() == EstadoProceso.TERMINADO) {
                sumaEspera += proceso.tiempoEspera();
                sumaRetorno += proceso.tiempoRetorno();
                terminados++;
            }
        }
        Double esperaPromedio = terminados == 0 ? null : (double) sumaEspera / terminados;
        Double retornoPromedio = terminados == 0 ? null : (double) sumaRetorno / terminados;
        Double utilizacionCpu = instanteActual == 0 ? null : (double) ticksCpuOcupada / instanteActual * 100;
        return new Metricas(esperaPromedio, retornoPromedio, utilizacionCpu, terminados, procesos.size());
    }
}
