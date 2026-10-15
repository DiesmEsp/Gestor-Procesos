package gestor.recursos.memoria;

import java.util.List;
import java.util.Optional;

/** Best-Fit: menor hueco suficiente; ante un empate, menor dirección base (FR-014). */
public final class MejorAjuste implements PoliticaAsignacion {
    @Override
    public Optional<Bloque> elegirHueco(List<Bloque> huecos, int tamano) {
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño solicitado debe ser positivo");
        }
        Bloque mejor = null;
        for (Bloque hueco : huecos) {
            if (hueco.esHueco() && hueco.tamano() >= tamano
                    && (mejor == null || hueco.tamano() < mejor.tamano()
                    || (hueco.tamano() == mejor.tamano() && hueco.base() < mejor.base()))) {
                mejor = hueco;
            }
        }
        return Optional.ofNullable(mejor);
    }

    @Override
    public String nombre() {
        return "Best-Fit";
    }
}
