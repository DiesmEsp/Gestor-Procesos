package gestor.interfaz.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import gestor.json.ErrorJson;
import gestor.json.EscritorJson;
import gestor.json.LectorJson;
import gestor.modelo.PoliticaMemoria;
import gestor.motor.ControladorSimulacion;
import gestor.motor.ModoSimulacion;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Enrutamiento y validación HTTP; ninguna petición modifica parcialmente la configuración. */
public final class ManejadorApi implements HttpHandler {
    private final ControladorSimulacion controlador;

    /**
     * Asocia las rutas HTTP al controlador compartido.
     *
     * @param controlador controlador compartido con el reloj
     */
    public ManejadorApi(ControladorSimulacion controlador) { this.controlador = controlador; }

    /**
     * Atiende una solicitud HTTP y cierra el intercambio al completar la respuesta.
     *
     * @param intercambio solicitud y canal de respuesta HTTP
     * @throws IOException si falla el enlace del puerto o la comunicación HTTP
     */
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            responderJson(intercambio, 200, atender(intercambio));
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (RuntimeException excepcion) {
            responderError(intercambio, 500, "ERROR_INTERNO", "No se pudo atender la solicitud");
        } finally {
            intercambio.close();
        }
    }

    private Object atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getRawPath();
        if (ruta.equals("/api/estado")) {
            exigirMetodo(intercambio, "GET");
            long desde = desdeEvento(intercambio.getRequestURI().getRawQuery());
            return ConvertidorInstantanea.aMapa(controlador.instantanea(), desde);
        }
        if (ruta.startsWith("/api/proceso/")) {
            exigirMetodo(intercambio, "GET");
            String id = decodificar(ruta.substring("/api/proceso/".length()).replace("+", "%2B"));
            var estado = controlador.instantanea();
            var proceso = estado.procesos().stream().filter(candidato -> candidato.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ErrorHttp(404, "PROCESO_NO_EXISTE", "El proceso '" + id + "' no existe"));
            return ConvertidorInstantanea.proceso(proceso, estado.instante());
        }
        if (Set.of("/api/control/iniciar", "/api/control/pausar", "/api/control/reiniciar").contains(ruta)) {
            exigirMetodo(intercambio, "POST");
            synchronized (controlador) {
                try {
                    switch (ruta) {
                        case "/api/control/iniciar" -> controlador.iniciar();
                        case "/api/control/pausar" -> controlador.pausar();
                        case "/api/control/reiniciar" -> controlador.reiniciar();
                    }
                } catch (IllegalStateException excepcion) {
                    throw new ErrorHttp(409, "TRANSICION_INVALIDA", excepcion.getMessage());
                }
                return ConvertidorInstantanea.aMapa(controlador.instantanea(), 0);
            }
        }
        if (ruta.equals("/api/configuracion")) {
            exigirMetodo(intercambio, "POST");
            return configurar(intercambio);
        }
        throw new ErrorHttp(404, "RUTA_NO_EXISTE", "La ruta solicitada no existe");
    }

    private Object configurar(HttpExchange intercambio) throws IOException {
        Object cuerpo;
        try {
            String texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(intercambio.getRequestBody().readAllBytes())).toString();
            cuerpo = LectorJson.leer(texto);
        } catch (ErrorJson | CharacterCodingException excepcion) {
            throw invalido("El cuerpo debe contener JSON válido en UTF-8: " + excepcion.getMessage());
        }
        if (!(cuerpo instanceof Map<?, ?> campos)) throw invalido("La configuración debe ser un objeto JSON");
        for (Object clave : campos.keySet()) {
            if (!Set.of("quantum", "memoriaTotal", "velocidadMs", "politicaMemoria").contains(clave)) {
                throw invalido("Campo de configuración no permitido: " + clave);
            }
        }
        Integer quantum = enteroOpcional(campos, "quantum", 1, Integer.MAX_VALUE);
        Integer memoria = enteroOpcional(campos, "memoriaTotal", 1, Integer.MAX_VALUE);
        Integer velocidad = enteroOpcional(campos, "velocidadMs", 50, 2000);
        PoliticaMemoria politica = null;
        if (campos.containsKey("politicaMemoria")) {
            if (!(campos.get("politicaMemoria") instanceof String codigo)) throw invalido("politicaMemoria debe ser una cadena");
            politica = PoliticaMemoria.desdeCodigo(codigo)
                    .orElseThrow(() -> invalido("politicaMemoria debe ser uno de: " + PoliticaMemoria.valoresValidos()));
        }
        // Las precondiciones y la aplicación comparten el monitor del reloj: ningún tick puede intercalarse.
        synchronized (controlador) {
            var estado = controlador.instantanea();
            if ((memoria != null || politica != null) && estado.modo() != ModoSimulacion.DETENIDA) {
                throw new ErrorHttp(409, "MEMORIA_NO_EDITABLE", "La RAM y la política de memoria solo pueden modificarse antes de iniciar");
            }
            if (memoria != null) {
                for (var proceso : estado.procesos()) {
                    if (proceso.memoria() > memoria) throw new ErrorHttp(409, "MEMORIA_INSUFICIENTE",
                            "El proceso '" + proceso.id() + "' no cabe en " + memoria + " MB de RAM");
                }
            }
            if (memoria != null) controlador.cambiarMemoriaTotal(memoria);
            if (politica != null) controlador.cambiarPoliticaMemoria(politica);
            if (quantum != null) controlador.cambiarQuantum(quantum);
            if (velocidad != null) controlador.cambiarVelocidad(velocidad);
            return ConvertidorInstantanea.aMapa(controlador.instantanea(), 0);
        }
    }

    private static Integer enteroOpcional(Map<?, ?> campos, String campo, int minimo, int maximo) {
        if (!campos.containsKey(campo)) return null;
        if (!(campos.get(campo) instanceof Long valor) || valor < minimo || valor > maximo) {
            throw invalido(campo + " debe ser un entero entre " + minimo + " y " + maximo);
        }
        return valor.intValue();
    }

    private static long desdeEvento(String consulta) {
        long resultado = 0;
        boolean encontrado = false;
        if (consulta == null) return resultado;
        for (String parametro : consulta.split("&", -1)) {
            String[] par = parametro.split("=", 2);
            if (!decodificar(par[0]).equals("desdeEvento")) continue;
            if (encontrado || par.length != 2) throw invalido("desdeEvento debe aparecer una vez y ser un entero mayor o igual a 0");
            encontrado = true;
            String valor = decodificar(par[1]);
            try {
                if (!valor.matches("[0-9]+")) throw new NumberFormatException();
                resultado = Long.parseLong(valor);
            } catch (NumberFormatException excepcion) {
                throw invalido("desdeEvento debe ser un entero mayor o igual a 0");
            }
        }
        return resultado;
    }

    private static String decodificar(String texto) {
        try { return URLDecoder.decode(texto, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException excepcion) { throw invalido("La codificación de la URL no es válida"); }
    }

    static void exigirMetodo(HttpExchange intercambio, String metodo) {
        if (!intercambio.getRequestMethod().equals(metodo)) {
            intercambio.getResponseHeaders().set("Allow", metodo);
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Esta ruta requiere el método " + metodo);
        }
    }

    static void responderJson(HttpExchange intercambio, int estado, Object cuerpo) throws IOException {
        byte[] bytes = EscritorJson.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
    }

    static void responderError(HttpExchange intercambio, int estado, String codigo, String mensaje) throws IOException {
        responderJson(intercambio, estado, Map.of("error", codigo, "mensaje", mensaje));
    }

    private static ErrorHttp invalido(String mensaje) { return new ErrorHttp(400, "VALOR_INVALIDO", mensaje); }

    static final class ErrorHttp extends RuntimeException {
        final int estado;
        final String codigo;
        ErrorHttp(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
    }
}
