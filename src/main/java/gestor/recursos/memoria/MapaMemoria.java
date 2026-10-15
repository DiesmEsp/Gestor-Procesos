package gestor.recursos.memoria;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** RAM de particiones variables, ordenadas y contiguas, sin huecos adyacentes. */
public final class MapaMemoria {
    private final int total;
    private final PoliticaAsignacion politica;
    private final List<Bloque> bloques = new ArrayList<>();

    public MapaMemoria(int memoriaTotal, PoliticaAsignacion politica) {
        if (memoriaTotal < 1) {
            throw new IllegalArgumentException("La memoria total debe ser mayor o igual a 1 MB");
        }
        this.total = memoriaTotal;
        this.politica = Objects.requireNonNull(politica, "La política de memoria no puede ser nula");
        bloques.add(new Bloque(0, memoriaTotal, null));
    }

    public Optional<Bloque> asignar(String id, int tamano) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El identificador del proceso no puede estar vacío");
        }
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño solicitado debe ser positivo");
        }
        if (bloqueDe(id).isPresent()) {
            throw new IllegalArgumentException("El proceso " + id + " ya tiene memoria asignada");
        }

        List<Bloque> huecos = bloques.stream().filter(Bloque::esHueco).toList();
        Optional<Bloque> elegido = politica.elegirHueco(huecos, tamano);
        if (elegido.isEmpty()) {
            return Optional.empty();
        }

        Bloque hueco = elegido.orElseThrow();
        int indice = bloques.indexOf(hueco);
        Bloque asignado = new Bloque(hueco.base(), tamano, id);
        bloques.set(indice, asignado);
        int sobrante = hueco.tamano() - tamano;
        if (sobrante > 0) {
            bloques.add(indice + 1, new Bloque(hueco.base() + tamano, sobrante, null));
        }
        return Optional.of(asignado);
    }

    /** Libera el bloque y fusiona los huecos vecinos en ambos sentidos (FR-016). */
    public void liberar(String id) {
        Bloque ocupado = bloqueDe(id).orElseThrow(
                () -> new IllegalArgumentException("No hay memoria asignada al proceso " + id));
        int indice = bloques.indexOf(ocupado);
        bloques.set(indice, new Bloque(ocupado.base(), ocupado.tamano(), null));

        if (indice > 0 && bloques.get(indice - 1).esHueco()) {
            Bloque anterior = bloques.get(indice - 1);
            Bloque actual = bloques.get(indice);
            bloques.set(indice - 1, new Bloque(anterior.base(), anterior.tamano() + actual.tamano(), null));
            bloques.remove(indice);
            indice--;
        }
        if (indice + 1 < bloques.size() && bloques.get(indice + 1).esHueco()) {
            Bloque actual = bloques.get(indice);
            Bloque siguiente = bloques.get(indice + 1);
            bloques.set(indice, new Bloque(actual.base(), actual.tamano() + siguiente.tamano(), null));
            bloques.remove(indice + 1);
        }
    }

    public int memoriaLibre() {
        return bloques.stream().filter(Bloque::esHueco).mapToInt(Bloque::tamano).sum();
    }

    public int mayorHueco() {
        return bloques.stream().filter(Bloque::esHueco).mapToInt(Bloque::tamano).max().orElse(0);
    }

    /** FR-017: fragmentación externa = memoria libre − mayor hueco contiguo. */
    public int fragmentacionExterna() {
        return memoriaLibre() - mayorHueco();
    }

    public boolean cabe(int tamano) {
        return tamano > 0 && bloques.stream().anyMatch(bloque -> bloque.esHueco() && bloque.tamano() >= tamano);
    }

    public Optional<Bloque> bloqueDe(String id) {
        return bloques.stream().filter(bloque -> !bloque.esHueco() && Objects.equals(id, bloque.idProceso()))
                .findFirst();
    }

    public List<Bloque> bloques() {
        return List.copyOf(bloques);
    }

    /** Copia independiente del mapa; sus registros de bloques son inmutables. */
    public MapaMemoria copia() {
        MapaMemoria copia = new MapaMemoria(total, politica);
        copia.bloques.clear();
        copia.bloques.addAll(bloques);
        return copia;
    }

    public int total() {
        return total;
    }
}
