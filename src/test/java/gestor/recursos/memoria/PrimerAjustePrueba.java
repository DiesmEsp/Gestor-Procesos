package gestor.recursos.memoria;

import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import java.util.Optional;

/** Selección First-Fit según FR-014 y el escenario 8 de la Historia 2. */
public class PrimerAjustePrueba {
    @Prueba
    public void eligeElHuecoDeBaseCeroParaVeinticincoMb() {
        PrimerAjuste politica = new PrimerAjuste();
        Bloque primerHueco = new Bloque(0, 50, null);
        Bloque segundoHueco = new Bloque(70, 30, null);

        Optional<Bloque> elegido = politica.elegirHueco(List.of(primerHueco, segundoHueco), 25);

        Afirmar.iguales(Optional.of(primerHueco), elegido,
                "Entre [0–49] y [70–99], First-Fit elige el primer hueco, de base 0");
    }

    @Prueba
    public void conservaElPrimerHuecoAunqueMasAdelanteHayaUnoExacto() {
        PrimerAjuste politica = new PrimerAjuste();
        Bloque primerHueco = new Bloque(0, 50, null);
        Bloque huecoExacto = new Bloque(70, 25, null);

        Optional<Bloque> elegido = politica.elegirHueco(List.of(primerHueco, huecoExacto), 25);

        Afirmar.iguales(Optional.of(primerHueco), elegido,
                "First-Fit se detiene en el primero que cabe aunque no sea el más pequeño");
    }

    @Prueba
    public void omiteLosHuecosInicialesQueNoAlcanzan() {
        PrimerAjuste politica = new PrimerAjuste();
        Bloque insuficiente = new Bloque(0, 10, null);
        Bloque primerSuficiente = new Bloque(30, 50, null);
        Bloque exactoPosterior = new Bloque(90, 25, null);

        Optional<Bloque> elegido = politica.elegirHueco(
                List.of(insuficiente, primerSuficiente, exactoPosterior), 25);

        Afirmar.iguales(Optional.of(primerSuficiente), elegido,
                "Se omiten huecos insuficientes y se toma el primero que sí alcanza");
    }

    @Prueba
    public void aceptaUnHuecoQueTieneExactamenteElTamanoSolicitado() {
        PrimerAjuste politica = new PrimerAjuste();
        Bloque insuficiente = new Bloque(0, 10, null);
        Bloque huecoExacto = new Bloque(70, 25, null);

        Optional<Bloque> elegido = politica.elegirHueco(List.of(insuficiente, huecoExacto), 25);

        Afirmar.iguales(Optional.of(huecoExacto), elegido,
                "El primer hueco suficiente también puede tener exactamente 25 MB");
    }

    @Prueba
    public void devuelveVacioSiNingunHuecoEsSuficiente() {
        PrimerAjuste politica = new PrimerAjuste();

        Optional<Bloque> elegido = politica.elegirHueco(
                List.of(new Bloque(0, 10, null), new Bloque(70, 20, null)), 25);

        Afirmar.iguales(Optional.empty(), elegido, "Ningún hueco alcanza los 25 MB solicitados");
    }

    @Prueba
    public void devuelveVacioCuandoNoHayHuecos() {
        PrimerAjuste politica = new PrimerAjuste();

        Afirmar.iguales(Optional.empty(), politica.elegirHueco(List.of(), 1),
                "No se puede asignar memoria sin huecos");
    }

    @Prueba
    public void exponeElNombreFirstFit() {
        Afirmar.iguales("First-Fit", new PrimerAjuste().nombre(), "La política debe identificarse como First-Fit");
    }
}
