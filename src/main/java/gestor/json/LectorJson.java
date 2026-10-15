package gestor.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Analizador recursivo descendente de JSON, sin dependencias ni estado compartido. */
public final class LectorJson {
    private final String texto;
    private int posicion;
    private int linea = 1;
    private int columna = 1;

    private LectorJson(String texto) {
        this.texto = texto;
    }

    public static Object leer(String texto) {
        if (texto == null) {
            throw new ErrorJson(1, 1, "se esperaba un valor JSON");
        }
        LectorJson lector = new LectorJson(texto);
        Object valor = lector.valor();
        lector.omitirEspacios();
        if (!lector.fin()) {
            throw lector.error("contenido sobrante tras el valor raíz");
        }
        return valor;
    }

    private Object valor() {
        omitirEspacios();
        if (fin()) {
            throw error("se esperaba un valor JSON");
        }
        return switch (actual()) {
            case '{' -> objeto();
            case '[' -> arreglo();
            case '"' -> cadena();
            case 't' -> literal("true", true);
            case 'f' -> literal("false", false);
            case 'n' -> literal("null", null);
            default -> {
                if (actual() == '-' || esDigito(actual())) {
                    yield numero();
                }
                throw error("se esperaba un valor JSON");
            }
        };
    }

    private Map<String, Object> objeto() {
        avanzar();
        Map<String, Object> campos = new LinkedHashMap<>();
        omitirEspacios();
        if (consumir('}')) {
            return campos;
        }
        while (true) {
            omitirEspacios();
            if (fin() || actual() != '"') {
                throw error("se esperaba una clave entre comillas");
            }
            String clave = cadena();
            omitirEspacios();
            if (!consumir(':')) {
                throw error("se esperaba ':'");
            }
            campos.put(clave, valor());
            omitirEspacios();
            if (consumir('}')) {
                return campos;
            }
            if (!consumir(',')) {
                throw error("se esperaba ',' o '}'");
            }
        }
    }

    private List<Object> arreglo() {
        avanzar();
        List<Object> elementos = new ArrayList<>();
        omitirEspacios();
        if (consumir(']')) {
            return elementos;
        }
        while (true) {
            elementos.add(valor());
            omitirEspacios();
            if (consumir(']')) {
                return elementos;
            }
            if (!consumir(',')) {
                throw error("se esperaba ',' o ']'");
            }
        }
    }

    private String cadena() {
        avanzar();
        StringBuilder resultado = new StringBuilder();
        while (!fin()) {
            if (actual() < 0x20) {
                throw error("carácter de control sin escapar en una cadena");
            }
            char caracter = avanzar();
            if (caracter == '"') {
                return resultado.toString();
            }
            if (caracter != '\\') {
                resultado.append(caracter);
                continue;
            }
            if (fin()) {
                throw error("cadena sin cerrar");
            }
            resultado.append(switch (avanzar()) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> throw error("escape de cadena no válido");
            });
        }
        throw error("cadena sin cerrar");
    }

    private char unicode() {
        int codigo = 0;
        for (int indice = 0; indice < 4; indice++) {
            if (fin()) {
                throw error("escape Unicode incompleto; se esperaban cuatro dígitos hexadecimales");
            }
            char caracter = actual();
            int digito;
            if (esDigito(caracter)) {
                digito = caracter - '0';
            } else if (caracter >= 'a' && caracter <= 'f') {
                digito = caracter - 'a' + 10;
            } else if (caracter >= 'A' && caracter <= 'F') {
                digito = caracter - 'A' + 10;
            } else {
                throw error("escape Unicode no válido; se esperaba un dígito hexadecimal");
            }
            avanzar();
            codigo = codigo * 16 + digito;
        }
        return (char) codigo;
    }

    private Object literal(String esperado, Object valor) {
        for (int indice = 0; indice < esperado.length(); indice++) {
            if (fin() || actual() != esperado.charAt(indice)) {
                throw error("se esperaba '" + esperado + "'");
            }
            avanzar();
        }
        return valor;
    }

    private Number numero() {
        int inicio = posicion;
        consumir('-');
        if (consumir('0')) {
            if (!fin() && esDigito(actual())) {
                throw error("no se permiten ceros iniciales en un número");
            }
        } else {
            exigirDigitos();
        }
        boolean decimal = false;
        if (consumir('.')) {
            decimal = true;
            exigirDigitos();
        }
        if (consumir('e') || consumir('E')) {
            decimal = true;
            if (!consumir('+')) {
                consumir('-');
            }
            exigirDigitos();
        }
        String representacion = texto.substring(inicio, posicion);
        try {
            if (!decimal) {
                return Long.valueOf(representacion);
            }
            double valor = Double.parseDouble(representacion);
            if (!Double.isFinite(valor)) {
                throw error("número fuera del rango admitido");
            }
            return valor;
        } catch (NumberFormatException excepcion) {
            throw error("número fuera del rango admitido");
        }
    }

    private void exigirDigitos() {
        if (fin() || !esDigito(actual())) {
            throw error("se esperaba un dígito en el número");
        }
        while (!fin() && esDigito(actual())) {
            avanzar();
        }
    }

    private boolean esDigito(char caracter) {
        return caracter >= '0' && caracter <= '9';
    }

    private void omitirEspacios() {
        while (!fin() && (actual() == ' ' || actual() == '\t' || actual() == '\r' || actual() == '\n')) {
            avanzar();
        }
    }

    private boolean consumir(char esperado) {
        if (!fin() && actual() == esperado) {
            avanzar();
            return true;
        }
        return false;
    }

    private char actual() {
        return texto.charAt(posicion);
    }

    private boolean fin() {
        return posicion == texto.length();
    }

    private char avanzar() {
        char caracter = texto.charAt(posicion++);
        if (caracter == '\r') {
            linea++;
            columna = 1;
        } else if (caracter == '\n') {
            if (posicion < 2 || texto.charAt(posicion - 2) != '\r') {
                linea++;
            }
            columna = 1;
        } else {
            columna++;
        }
        return caracter;
    }

    private ErrorJson error(String motivo) {
        return new ErrorJson(linea, columna, motivo);
    }
}
