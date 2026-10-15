package gestor.modelo;

import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import java.util.Set;

/** Contrato del PCB: transiciones de data-model.md y fórmulas FR-027 y FR-028. */
public class TransicionesPcbPrueba {
    private static final Set<List<EstadoProceso>> TRANSICIONES_VALIDAS = Set.of(
            List.of(EstadoProceso.PENDIENTE_LLEGADA, EstadoProceso.NUEVO),
            List.of(EstadoProceso.NUEVO, EstadoProceso.LISTO),
            List.of(EstadoProceso.LISTO, EstadoProceso.EJECUCION),
            List.of(EstadoProceso.EJECUCION, EstadoProceso.LISTO),
            List.of(EstadoProceso.EJECUCION, EstadoProceso.BLOQUEADO),
            List.of(EstadoProceso.BLOQUEADO, EstadoProceso.LISTO),
            List.of(EstadoProceso.EJECUCION, EstadoProceso.TERMINADO));

    @Prueba
    public void comienzaPendienteDeLlegada() {
        Proceso proceso = crearProceso();

        Afirmar.iguales(EstadoProceso.PENDIENTE_LLEGADA, proceso.estado(),
                "Un proceso recién creado todavía no ha llegado");
    }

    @Prueba
    public void aceptaLasSieteTransicionesValidas() {
        for (EstadoProceso origen : EstadoProceso.values()) {
            for (EstadoProceso destino : EstadoProceso.values()) {
                if (!TRANSICIONES_VALIDAS.contains(List.of(origen, destino))) {
                    continue;
                }
                Proceso proceso = crearEnEstado(origen);

                Afirmar.verdadero(origen.puedeTransicionarA(destino),
                        "El estado debe permitir la transición " + origen + " a " + destino);
                proceso.cambiarEstado(destino);

                Afirmar.iguales(destino, proceso.estado(), "El PCB debe cambiar al estado solicitado");
            }
        }
    }

    @Prueba
    public void rechazaTodasLasDemasTransicionesSinAlterarElEstado() {
        for (EstadoProceso origen : EstadoProceso.values()) {
            for (EstadoProceso destino : EstadoProceso.values()) {
                if (TRANSICIONES_VALIDAS.contains(List.of(origen, destino))) {
                    continue;
                }
                Proceso proceso = crearEnEstado(origen);

                Afirmar.falso(origen.puedeTransicionarA(destino),
                        "El estado debe rechazar la transición " + origen + " a " + destino);
                IllegalStateException excepcion = Afirmar.lanza(IllegalStateException.class,
                        () -> proceso.cambiarEstado(destino));

                Afirmar.iguales("Transición inválida de " + origen + " a " + destino + " para el proceso P1",
                        excepcion.getMessage(), "El error debe explicar la transición en español");
                Afirmar.iguales(origen, proceso.estado(), "Una transición rechazada conserva el estado");
            }
        }
    }

    @Prueba
    public void calculaTicksRestantesComoRafagaMenosConsumo() {
        Proceso proceso = crearProceso();
        Afirmar.iguales(10, proceso.ticksRestantes(), "Antes de ejecutar queda toda la ráfaga");

        proceso.fijarTicksConsumidos(3);
        Afirmar.iguales(7, proceso.ticksRestantes(), "Se descuentan los ticks de CPU consumidos");

        proceso.fijarTicksConsumidos(10);
        Afirmar.iguales(0, proceso.ticksRestantes(), "Al consumir toda la ráfaga no quedan ticks");
    }

    @Prueba
    public void sumaLasEsperasEnListoYEnMemoria() {
        Proceso proceso = crearProceso();
        Afirmar.iguales(0, proceso.tiempoEspera(), "La espera inicial es cero");

        proceso.fijarTicksEsperaListo(4);
        Afirmar.iguales(4, proceso.tiempoEspera(), "La espera incluye los ticks en Listo");

        proceso.fijarTicksEsperaMemoria(6);
        Afirmar.iguales(10, proceso.tiempoEspera(), "La espera de memoria también cuenta");
    }

    @Prueba
    public void calculaRetornoComoFinalizacionMenosLlegada() {
        Proceso proceso = crearEnEstado(EstadoProceso.EJECUCION);
        proceso.fijarTicksConsumidos(10);
        proceso.fijarInstanteFinalizacion(18);
        proceso.cambiarEstado(EstadoProceso.TERMINADO);

        Afirmar.iguales(14, proceso.tiempoRetorno(),
                "Llegada en 4 y finalización en 18 producen 14 ticks de retorno");
    }

    // Los mutadores fijar... preparan los campos del PCB mutable de T020 sin usar reflexión.
    private Proceso crearProceso() {
        return new Proceso(new DefinicionProceso("P1", 4, 10, 20, null, List.of(), 0));
    }

    private Proceso crearEnEstado(EstadoProceso estado) {
        Proceso proceso = crearProceso();
        if (estado == EstadoProceso.PENDIENTE_LLEGADA) {
            return proceso;
        }
        proceso.cambiarEstado(EstadoProceso.NUEVO);
        if (estado == EstadoProceso.NUEVO) {
            return proceso;
        }
        proceso.cambiarEstado(EstadoProceso.LISTO);
        if (estado == EstadoProceso.LISTO) {
            return proceso;
        }
        proceso.cambiarEstado(EstadoProceso.EJECUCION);
        if (estado != EstadoProceso.EJECUCION) {
            proceso.cambiarEstado(estado);
        }
        return proceso;
    }
}
