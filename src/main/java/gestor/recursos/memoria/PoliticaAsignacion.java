package gestor.recursos.memoria;

import java.util.List;
import java.util.Optional;

/** Estrategia que selecciona un hueco sin modificar la lista recibida. */
public interface PoliticaAsignacion {
    /** Recibe huecos ordenados por dirección base y un tamaño positivo en MB. */
    Optional<Bloque> elegirHueco(List<Bloque> huecos, int tamano);

    String nombre();
}
