package gestor.modelo;

/** Solicitud de E/S; la capa de entrada valida el instante de CPU y la duración. */
public record EventoES(int enQuantum, int duracion) {
}
