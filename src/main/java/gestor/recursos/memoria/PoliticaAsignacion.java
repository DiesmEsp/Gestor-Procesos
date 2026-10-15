package gestor.recursos.memoria;

import java.util.List;
import java.util.Optional;

/** Estrategia que selecciona un hueco sin modificar la lista recibida. */
public interface PoliticaAsignacion {
    /**
     * Recibe huecos ordenados por dirección base y un tamaño positivo en MB.
     *
     * @param huecos huecos disponibles ordenados por dirección base
     * @param tamano tamaño positivo del bloque en MB
     * @return el hueco elegido, o vacío si ninguno alcanza
     */
    Optional<Bloque> elegirHueco(List<Bloque> huecos, int tamano);

    /**
     * Devuelve el nombre de la estrategia para las interfaces.
     *
     * @return la etiqueta de la estrategia
     */
    String nombre();
}
