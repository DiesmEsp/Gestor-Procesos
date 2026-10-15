package gestor.recursos.memoria;

import java.util.List;
import java.util.Optional;

/** First-Fit: primer hueco suficiente en orden de dirección base (FR-014). */
public final class PrimerAjuste implements PoliticaAsignacion {
    @Override
    public Optional<Bloque> elegirHueco(List<Bloque> huecos, int tamano) {
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño solicitado debe ser positivo");
        }
        for (Bloque hueco : huecos) {
            if (hueco.esHueco() && hueco.tamano() >= tamano) {
                return Optional.of(hueco);
            }
        }
        return Optional.empty();
    }

    @Override
    public String nombre() {
        return "First-Fit";
    }
}
