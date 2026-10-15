package gestor.recursos.memoria;

import gestor.pruebas.Afirmar;
import gestor.pruebas.InvariantesMemoria;
import gestor.pruebas.Prueba;
import java.util.List;
import java.util.Optional;

/** Escenarios de partición variable, fusión FR-016 y fragmentación FR-017. */
public class MapaMemoriaPrueba {
    @Prueba
    public void comienzaConUnHuecoQueCubreTodaLaRam() {
        MapaMemoria mapa = crearMapa();

        Afirmar.iguales(100, mapa.total(), "La capacidad es de 100 MB");
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), mapa.bloques(),
                "El hueco inicial cubre las direcciones de 0 a 99");
        verificarMetricas(mapa, 100, 100, 0);
    }

    @Prueba
    public void asignaAlInicioYDejaElSobranteComoHueco() {
        MapaMemoria mapa = crearMapa();

        Bloque asignado = asignar(mapa, "P1", 30);

        Afirmar.iguales(new Bloque(0, 30, "P1"), asignado, "El proceso ocupa el inicio del hueco");
        Afirmar.iguales(29, asignado.limite(), "El límite asignado es inclusivo");
        Afirmar.iguales(List.of(new Bloque(0, 30, "P1"), new Bloque(30, 70, null)), mapa.bloques(),
                "Los 70 MB sobrantes quedan como hueco");
        verificarMetricas(mapa, 70, 70, 0);
    }

    @Prueba
    public void fusionaLosHuecosDelEscenarioTresDeLaHistoriaDos() {
        MapaMemoria mapa = crearMapaConTresProcesos();
        Afirmar.iguales(List.of(new Bloque(0, 50, "A"), new Bloque(50, 20, "B"),
                new Bloque(70, 20, "C"), new Bloque(90, 10, null)), mapa.bloques(),
                "La disposición inicial debe coincidir con el escenario de referencia");
        verificarMetricas(mapa, 10, 10, 0);

        liberar(mapa, "A");
        Afirmar.iguales(List.of(new Bloque(0, 50, null), new Bloque(50, 20, "B"),
                new Bloque(70, 20, "C"), new Bloque(90, 10, null)), mapa.bloques(),
                "Liberar A deja un hueco separado por los procesos B y C");
        verificarMetricas(mapa, 60, 50, 10);

        liberar(mapa, "C");
        Afirmar.iguales(List.of(new Bloque(0, 50, null), new Bloque(50, 20, "B"),
                new Bloque(70, 30, null)), mapa.bloques(),
                "Liberar C fusiona su bloque con el hueco posterior hasta la dirección 99");
        verificarMetricas(mapa, 80, 50, 30);

        liberar(mapa, "B");
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), mapa.bloques(),
                "Liberar B fusiona simultáneamente los huecos anterior y posterior");
        verificarMetricas(mapa, 100, 100, 0);
    }

    @Prueba
    public void fusionaConElHuecoAnteriorAunqueElSiguienteEsteOcupado() {
        MapaMemoria mapa = crearMapa();
        asignar(mapa, "A", 30);
        asignar(mapa, "B", 30);
        asignar(mapa, "C", 40);

        liberar(mapa, "A");
        liberar(mapa, "B");

        Afirmar.iguales(List.of(new Bloque(0, 60, null), new Bloque(60, 40, "C")), mapa.bloques(),
                "Los huecos de A y B se fusionan sin alterar C");
        verificarMetricas(mapa, 60, 60, 0);
    }

    @Prueba
    public void unaAsignacionExactaNoDejaHuecosDeTamanoCero() {
        MapaMemoria mapa = crearMapa();
        Bloque asignado = asignar(mapa, "P1", 100);

        Afirmar.iguales(new Bloque(0, 100, "P1"), asignado, "P1 ocupa toda la RAM");
        Afirmar.iguales(List.of(asignado), mapa.bloques(), "No debe quedar un hueco vacío");
        verificarMetricas(mapa, 0, 0, 0);

        liberar(mapa, "P1");
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), mapa.bloques(), "Se recupera toda la RAM");
        verificarMetricas(mapa, 100, 100, 0);
    }

    @Prueba
    public void devuelveVacioSinAlterarElMapaCuandoFaltaMemoriaTotal() {
        MapaMemoria mapa = crearMapa();
        asignar(mapa, "P1", 80);
        List<Bloque> anteriores = List.copyOf(mapa.bloques());

        Optional<Bloque> resultado = mapa.asignar("P2", 21);
        InvariantesMemoria.verificar(mapa);

        Afirmar.iguales(Optional.empty(), resultado, "Una solicitud que supera la RAM libre no se asigna");
        Afirmar.iguales(anteriores, mapa.bloques(), "Una asignación rechazada conserva todos los bloques");
        verificarMetricas(mapa, 20, 20, 0);
    }

    @Prueba
    public void devuelveVacioSiLaMemoriaLibreEstaFragmentada() {
        MapaMemoria mapa = crearMapaConTresProcesos();
        liberar(mapa, "A");
        liberar(mapa, "C");
        List<Bloque> anteriores = List.copyOf(mapa.bloques());

        Optional<Bloque> resultado = mapa.asignar("D", 60);
        InvariantesMemoria.verificar(mapa);

        Afirmar.iguales(Optional.empty(), resultado, "Hay 80 MB libres pero ningún hueco de 60 MB");
        Afirmar.iguales(anteriores, mapa.bloques(), "La fragmentación no debe provocar asignaciones parciales");
        verificarMetricas(mapa, 80, 50, 30);
    }

    @Prueba
    public void liberarUnProcesoInexistenteLanzaUnaExcepcionSinAlterarElMapa() {
        MapaMemoria mapa = crearMapa();
        asignar(mapa, "P1", 30);
        List<Bloque> anteriores = List.copyOf(mapa.bloques());

        Afirmar.lanza(IllegalArgumentException.class, () -> mapa.liberar("INEXISTENTE"));
        InvariantesMemoria.verificar(mapa);

        Afirmar.iguales(anteriores, mapa.bloques(), "Una liberación inválida conserva el mapa");
        verificarMetricas(mapa, 70, 70, 0);
    }

    @Prueba
    public void losBloquesSeExponenComoUnaCopiaInmutable() {
        MapaMemoria mapa = crearMapa();
        List<Bloque> copia = mapa.bloques();

        Afirmar.lanza(UnsupportedOperationException.class, () -> copia.add(new Bloque(100, 1, null)));
        InvariantesMemoria.verificar(mapa);
        Afirmar.lanza(UnsupportedOperationException.class, () -> copia.set(0, new Bloque(0, 100, "AJENO")));
        InvariantesMemoria.verificar(mapa);
        Afirmar.lanza(UnsupportedOperationException.class, copia::clear);
        InvariantesMemoria.verificar(mapa);

        asignar(mapa, "P1", 30);
        Afirmar.iguales(List.of(new Bloque(0, 100, null)), copia,
                "Una copia anterior no cambia al modificar posteriormente el mapa");
    }

    private MapaMemoria crearMapa() {
        MapaMemoria mapa = new MapaMemoria(100, new MejorAjuste());
        InvariantesMemoria.verificar(mapa);
        return mapa;
    }

    private MapaMemoria crearMapaConTresProcesos() {
        MapaMemoria mapa = crearMapa();
        asignar(mapa, "A", 50);
        asignar(mapa, "B", 20);
        asignar(mapa, "C", 20);
        return mapa;
    }

    private Bloque asignar(MapaMemoria mapa, String identificador, int tamano) {
        Optional<Bloque> resultado = mapa.asignar(identificador, tamano);
        InvariantesMemoria.verificar(mapa);
        Afirmar.verdadero(resultado.isPresent(), "Debe asignarse memoria al proceso " + identificador);
        return resultado.orElseThrow();
    }

    private void liberar(MapaMemoria mapa, String identificador) {
        mapa.liberar(identificador);
        InvariantesMemoria.verificar(mapa);
    }

    private void verificarMetricas(MapaMemoria mapa, int libre, int mayorHueco, int fragmentacion) {
        Afirmar.iguales(libre, mapa.memoriaLibre(), "La memoria libre es la suma de los huecos");
        Afirmar.iguales(mayorHueco, mapa.mayorHueco(), "El mayor hueco es el mayor espacio contiguo libre");
        Afirmar.iguales(fragmentacion, mapa.fragmentacionExterna(),
                "La fragmentación externa es la memoria libre menos el mayor hueco");
    }
}
