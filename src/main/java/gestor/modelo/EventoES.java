package gestor.modelo;

/**
 * Solicitud de E/S; la capa de entrada valida el instante de CPU y la duración.
 *
 * @param enQuantum ticks de CPU acumulados al solicitar E/S
 * @param duracion duración positiva de la E/S en ticks
 */
public record EventoES(int enQuantum, int duracion) {
}
