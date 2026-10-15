package gestor.recursos.memoria;

import java.util.List;
import java.util.Optional;

/** First-Fit: primer hueco suficiente en orden de dirección base (FR-014). */
public final class PrimerAjuste implements PoliticaAsignacion {
    /**
     * Crea una instancia lista para su primer uso, sin estado previo.
     */
    public PrimerAjuste() {}

    /**
     * Selecciona un hueco apto conforme a la política sin modificar la lista recibida.
     *
     * @param huecos huecos disponibles ordenados por dirección base
     * @param tamano tamaño positivo del bloque en MB
     * @return el hueco elegido, o vacío si ninguno alcanza
     */
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

    /**
     * Devuelve el nombre de la estrategia para las interfaces.
     *
     * @return la etiqueta de la estrategia
     */
    @Override
    public String nombre() {
        return "First-Fit";
    }
}
