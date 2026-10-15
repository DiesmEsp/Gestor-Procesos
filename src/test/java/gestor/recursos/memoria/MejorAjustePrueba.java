package gestor.recursos.memoria;

import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import java.util.Optional;

/** Selección Best-Fit según FR-014 y los escenarios 1 y 2 de la Historia 2. */
public class MejorAjustePrueba {
    @Prueba
    public void eligeElHuecoDeBaseSetentaParaVeinticincoMb() {
        MejorAjuste politica = new MejorAjuste();
        Bloque huecoInicial = new Bloque(0, 50, null);
        Bloque huecoMenor = new Bloque(70, 30, null);

        Optional<Bloque> elegido = politica.elegirHueco(List.of(huecoInicial, huecoMenor), 25);

        Afirmar.iguales(Optional.of(huecoMenor), elegido,
                "Entre [0–49] y [70–99], Best-Fit elige el hueco de 30 MB con base 70");
    }

    @Prueba
    public void desempataPorLaMenorDireccionBase() {
        MejorAjuste politica = new MejorAjuste();
        Bloque huecoDeMenorBase = new Bloque(10, 30, null);
        Bloque huecoDeMayorBase = new Bloque(70, 30, null);

        Optional<Bloque> elegido = politica.elegirHueco(List.of(huecoDeMenorBase, huecoDeMayorBase), 25);

        Afirmar.iguales(Optional.of(huecoDeMenorBase), elegido,
                "Entre huecos del mismo tamaño se elige el de menor base");
    }

    @Prueba
    public void prefiereUnHuecoExactoSobreUnoMayor() {
        MejorAjuste politica = new MejorAjuste();
        Bloque huecoMayor = new Bloque(0, 50, null);
        Bloque huecoExacto = new Bloque(70, 25, null);

        Optional<Bloque> elegido = politica.elegirHueco(List.of(huecoMayor, huecoExacto), 25);

        Afirmar.iguales(Optional.of(huecoExacto), elegido,
                "Un hueco exacto no deja sobrante y debe preferirse al de 50 MB");
    }

    @Prueba
    public void descartaLosHuecosMenoresQueLaSolicitudAntesDeComparar() {
        MejorAjuste politica = new MejorAjuste();
        Bloque insuficiente = new Bloque(0, 10, null);
        Bloque huecoMayor = new Bloque(30, 50, null);
        Bloque menorSuficiente = new Bloque(90, 30, null);

        Optional<Bloque> elegido = politica.elegirHueco(
                List.of(insuficiente, huecoMayor, menorSuficiente), 25);

        Afirmar.iguales(Optional.of(menorSuficiente), elegido,
                "El menor hueco absoluto no sirve si tiene menos memoria que la solicitada");
    }

    @Prueba
    public void devuelveVacioSiNingunHuecoEsSuficiente() {
        MejorAjuste politica = new MejorAjuste();

        Optional<Bloque> elegido = politica.elegirHueco(
                List.of(new Bloque(0, 10, null), new Bloque(70, 20, null)), 25);

        Afirmar.iguales(Optional.empty(), elegido,
                "Los huecos separados no se suman para alojar un proceso");
    }

    @Prueba
    public void devuelveVacioCuandoNoHayHuecos() {
        MejorAjuste politica = new MejorAjuste();

        Afirmar.iguales(Optional.empty(), politica.elegirHueco(List.of(), 1),
                "No se puede asignar memoria sin huecos");
    }

    @Prueba
    public void exponeElNombreBestFit() {
        Afirmar.iguales("Best-Fit", new MejorAjuste().nombre(), "La política debe identificarse como Best-Fit");
    }
}
