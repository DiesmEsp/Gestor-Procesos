package gestor.json;

import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gramática JSON y posiciones de error del analizador propio de T041. */
public class LectorJsonPrueba {
    @Prueba
    public void interpretaObjetosAnidadosConOrdenDeInsercion() {
        Map<?, ?> objeto = (Map<?, ?>) LectorJson.leer("{\"b\":2,\"a\":{\"id\":\"P1\"},\"nulo\":null}");
        Afirmar.verdadero(objeto instanceof LinkedHashMap, "El objeto conserva el orden de inserción");
        Afirmar.iguales(List.of("b", "a", "nulo"), List.copyOf(objeto.keySet()), "Se respeta el orden del archivo");
        Afirmar.iguales(2L, objeto.get("b"), "Los enteros se representan como Long");
        Afirmar.iguales(Map.of("id", "P1"), objeto.get("a"), "Se interpretan objetos anidados");
        Afirmar.verdadero(objeto.containsKey("nulo"), "Un valor nulo conserva su clave");
        Afirmar.nulo(objeto.get("nulo"), "El literal null produce un valor nulo");
        Afirmar.iguales(Map.of(), LectorJson.leer("{}"), "Se admiten objetos vacíos");
    }

    @Prueba
    public void interpretaListasHeterogeneasYVacias() {
        Afirmar.iguales(Arrays.asList("texto", 1L, true, null, List.of()),
                LectorJson.leer("[\"texto\",1,true,null,[]]"), "Se admiten arreglos heterogéneos y anidados");
    }

    @Prueba
    public void interpretaTodosLosEscapesUnicodeYTildes() {
        String json = "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00f1\\uD83D\\uDE00 simulación\"";
        Afirmar.iguales("\"\\/\b\f\n\r\tñ😀 simulación", LectorJson.leer(json),
                "Los escapes simples, Unicode y pares sustitutos conservan su contenido");
        Afirmar.iguales("", LectorJson.leer("\"\""), "Una cadena puede estar vacía");
    }

    @Prueba
    public void conservaElTipoLongDeLosEnterosIncluidosLosLimites() {
        for (String numero : List.of("0", "-0", "42", "-17", "9223372036854775807", "-9223372036854775808")) {
            Object valor = LectorJson.leer(numero);
            Afirmar.verdadero(valor instanceof Long, "Un entero no se convierte implícitamente a Double");
            Afirmar.iguales(Long.valueOf(numero), valor, "El valor entero se conserva exactamente");
        }
    }

    @Prueba
    public void interpretaDecimalesYExponentesComoDouble() {
        for (String numero : List.of("1.0", "-0.25", "1e3", "2E-2", "3e+2")) {
            Object valor = LectorJson.leer(numero);
            Afirmar.verdadero(valor instanceof Double, "Un decimal o exponente se representa como Double");
            Afirmar.iguales(Double.valueOf(numero), valor, "El valor decimal se conserva");
        }
    }

    @Prueba
    public void interpretaLiteralesYLosCuatroEspaciosJson() {
        Afirmar.iguales(true, LectorJson.leer(" \t\r\n true \n"), "Los espacios JSON se ignoran fuera de cadenas");
        Afirmar.iguales(false, LectorJson.leer("false"), "Literal falso");
        Afirmar.nulo(LectorJson.leer("null"), "Literal nulo");
    }

    @Prueba
    public void informaLineaColumnaYCausaExactas() {
        verificarError("", 1, 1, "se esperaba un valor JSON");
        verificarError("{\"a\":1", 1, 7, "se esperaba ',' o '}'");
        verificarError("\"hola", 1, 6, "cadena sin cerrar");
        verificarError("true false", 1, 6, "contenido sobrante tras el valor raíz");
        verificarError("[1,]", 1, 4, "se esperaba un valor JSON");
        verificarError("{\n  \"a\": 1\n  \"b\": 2\n}", 3, 3, "se esperaba ',' o '}'");
    }

    @Prueba
    public void cuentaUnaSolaLineaPorCadaSaltoWindows() {
        verificarError("{\r\n  \"a\": 1\r\n  \"b\": 2\r\n}", 3, 3, "se esperaba ',' o '}'");
        verificarError("{\r  \"a\": 1\r  \"b\": 2\r}", 3, 3, "se esperaba ',' o '}'");
    }

    @Prueba
    public void rechazaSintaxisNoJsonEnObjetosListasCadenasYNumeros() {
        for (String texto : List.of("{\"a\":1,}", "[", "{", "{a:1}", "{\"a\" 1}", "[1 2]",
                "\"\\x\"", "\"\\u00xz\"", "\"salto\nreal\"", "\"control" + (char) 1 + "\"",
                "01", "-01", "+1", "1.", "1e", "1e+", "-.1", ".5", "tru", "FALSE", "NaN", "Infinity")) {
            ErrorJson error = Afirmar.lanza(ErrorJson.class, () -> LectorJson.leer(texto));
            Afirmar.verdadero(error.linea() >= 1 && error.columna() >= 1, "Todo error tiene una posición válida");
            Afirmar.contiene(error.getMessage(), "línea ", "Los errores se informan en español");
        }
    }

    @Prueba
    public void rechazaNumerosFueraDeLaRepresentacionLongODoubleFinito() {
        for (String numero : List.of("9223372036854775808", "-9223372036854775809", "1e309", "-1e309")) {
            Afirmar.lanza(ErrorJson.class, () -> LectorJson.leer(numero));
        }
    }

    private void verificarError(String texto, int linea, int columna, String causa) {
        ErrorJson error = Afirmar.lanza(ErrorJson.class, () -> LectorJson.leer(texto));
        Afirmar.iguales(linea, error.linea(), "Línea exacta del error");
        Afirmar.iguales(columna, error.columna(), "Columna exacta del error");
        Afirmar.iguales("línea " + linea + ", columna " + columna + ": " + causa,
                error.getMessage(), "La excepción incluye posición y causa");
    }
}
