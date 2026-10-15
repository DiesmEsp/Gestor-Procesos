package gestor.json;

import java.util.List;
import java.util.Map;

/** Serializador JSON compacto de los tipos de la biblioteca estándar. */
public final class EscritorJson {
    private EscritorJson() {}

    public static String escribir(Object valor) {
        StringBuilder texto = new StringBuilder();
        agregar(valor, texto);
        return texto.toString();
    }

    private static void agregar(Object valor, StringBuilder texto) {
        if (valor == null) {
            texto.append("null");
        } else if (valor instanceof String cadena) {
            cadena(cadena, texto);
        } else if (valor instanceof Boolean) {
            texto.append(valor);
        } else if (valor instanceof Number numero) {
            String representacion = numero.toString();
            if (!representacion.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                throw new IllegalArgumentException("El número no tiene representación JSON: " + representacion);
            }
            texto.append(representacion);
        } else if (valor instanceof Map<?, ?> mapa) {
            texto.append('{');
            boolean primero = true;
            for (var entrada : mapa.entrySet()) {
                if (!(entrada.getKey() instanceof String clave)) {
                    throw new IllegalArgumentException("Las claves de un objeto JSON deben ser cadenas");
                }
                if (!primero) texto.append(',');
                primero = false;
                cadena(clave, texto);
                texto.append(':');
                agregar(entrada.getValue(), texto);
            }
            texto.append('}');
        } else if (valor instanceof List<?> lista) {
            texto.append('[');
            for (int indice = 0; indice < lista.size(); indice++) {
                if (indice > 0) texto.append(',');
                agregar(lista.get(indice), texto);
            }
            texto.append(']');
        } else {
            throw new IllegalArgumentException("Tipo no admitido en JSON: " + valor.getClass().getName());
        }
    }

    private static void cadena(String cadena, StringBuilder texto) {
        texto.append('"');
        for (int indice = 0; indice < cadena.length(); indice++) {
            char caracter = cadena.charAt(indice);
            switch (caracter) {
                case '"' -> texto.append("\\\"");
                case '\\' -> texto.append("\\\\");
                case '\b' -> texto.append("\\b");
                case '\f' -> texto.append("\\f");
                case '\n' -> texto.append("\\n");
                case '\r' -> texto.append("\\r");
                case '\t' -> texto.append("\\t");
                default -> {
                    if (caracter < 0x20 || Character.isSurrogate(caracter)) {
                        texto.append("\\u");
                        String hexadecimal = Integer.toHexString(caracter);
                        texto.append("0".repeat(4 - hexadecimal.length())).append(hexadecimal);
                    } else texto.append(caracter);
                }
            }
        }
        texto.append('"');
    }
}
