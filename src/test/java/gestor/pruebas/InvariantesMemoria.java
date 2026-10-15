package gestor.pruebas;

import gestor.recursos.memoria.Bloque;
import gestor.recursos.memoria.MapaMemoria;
import java.util.List;

/** Comprueba la cobertura contigua de la RAM y la fusión de huecos (SC-007). */
public final class InvariantesMemoria {
    private InvariantesMemoria() {
    }

    public static void verificar(MapaMemoria mapa) {
        List<Bloque> bloques = mapa.bloques();
        Afirmar.falso(bloques.isEmpty(), "La RAM debe estar representada por al menos un bloque");
        long baseEsperada = 0;
        long sumaTamanos = 0;
        boolean anteriorEraHueco = false;

        for (Bloque bloque : bloques) {
            Afirmar.verdadero(bloque.tamano() > 0, "Cada bloque debe tener tamaño positivo");
            Afirmar.iguales(baseEsperada, (long) bloque.base(),
                    "Los bloques deben ser contiguos, ordenados y sin solapamientos");
            Afirmar.iguales((long) bloque.base() + bloque.tamano() - 1, (long) bloque.limite(),
                    "El límite de cada bloque debe ser inclusivo");
            Afirmar.falso(anteriorEraHueco && bloque.esHueco(),
                    "No pueden existir dos huecos adyacentes");
            baseEsperada = (long) bloque.limite() + 1;
            sumaTamanos += bloque.tamano();
            anteriorEraHueco = bloque.esHueco();
        }

        Afirmar.iguales((long) mapa.total(), sumaTamanos,
                "La suma de bloques ocupados y huecos debe coincidir con la RAM total");
        Afirmar.iguales((long) mapa.total(), baseEsperada,
                "El último bloque debe terminar en la última dirección de la RAM");
    }
}
