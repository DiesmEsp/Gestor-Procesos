package gestor.json;

import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EscritorJsonPrueba {
    @Prueba
    public void serializaMapasEnOrdenConListasYNulos() {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("id", "P1");
        valores.put("datos", Arrays.asList(1, true, null));
        Afirmar.iguales("{\"id\":\"P1\",\"datos\":[1,true,null]}", EscritorJson.escribir(valores), "Orden y estructura exactos");
    }

    @Prueba
    public void escapaCadenasYConservaLasTildes() {
        String texto = "\"\\\b\f\n\r\t" + (char) 1 + " simulación 😀";
        String escrito = EscritorJson.escribir(texto);
        Afirmar.contiene(escrito, "\\u0001", "Los controles restantes usan escapes Unicode");
        Afirmar.contiene(escrito, "simulación", "Las tildes no se escapan");
        Afirmar.iguales(texto, LectorJson.leer(escrito), "Todos los escapes hacen ida y vuelta");
    }

    @Prueba
    public void serializaNumerosBooleanosYNulo() {
        Afirmar.iguales("[1,-2,2.5,100.0,true,false,null]",
                EscritorJson.escribir(Arrays.asList(1L, -2, 2.5, 100.0, true, false, null)), "Tipos escalares exactos");
        Afirmar.iguales("{}", EscritorJson.escribir(Map.of()), "Objeto vacío");
        Afirmar.iguales("[]", EscritorJson.escribir(List.of()), "Arreglo vacío");
    }

    @Prueba
    public void haceIdaYVueltaDeUnaEstructuraJson() {
        Object valor = LectorJson.leer("{\"numero\":2.5,\"lista\":[null,{\"texto\":\"ñ\\n\"}],\"activo\":true}");
        Afirmar.iguales(valor, LectorJson.leer(EscritorJson.escribir(valor)), "La serialización conserva la estructura");
    }

    @Prueba
    public void rechazaTiposYValoresQueNoTienenRepresentacionJson() {
        for (Object valor : List.of(Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, new Object(), Map.of(1, "x"))) {
            Afirmar.lanza(IllegalArgumentException.class, () -> EscritorJson.escribir(valor));
        }
    }
}
