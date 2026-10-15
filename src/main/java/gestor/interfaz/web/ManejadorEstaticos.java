package gestor.interfaz.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

/** Sirve exclusivamente los recursos web conocidos, empaquetados dentro del jar. */
public final class ManejadorEstaticos implements HttpHandler {
    private static final Map<String, String> TIPOS = Map.of("/", "text/html", "/estilos.css", "text/css", "/app.js", "text/javascript");

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            String ruta = intercambio.getRequestURI().getRawPath();
            String tipo = TIPOS.get(ruta);
            if (tipo == null) {
                ManejadorApi.responderError(intercambio, 404, "RUTA_NO_EXISTE", "La ruta solicitada no existe");
                return;
            }
            ManejadorApi.exigirMetodo(intercambio, "GET");
            String recurso = ruta.equals("/") ? "/web/index.html" : "/web" + ruta;
            try (var entrada = ManejadorEstaticos.class.getResourceAsStream(recurso)) {
                if (entrada == null) {
                    ManejadorApi.responderError(intercambio, 404, "RUTA_NO_EXISTE", "El recurso solicitado no existe");
                    return;
                }
                byte[] bytes = entrada.readAllBytes();
                intercambio.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
                intercambio.sendResponseHeaders(200, bytes.length);
                intercambio.getResponseBody().write(bytes);
            }
        } catch (ManejadorApi.ErrorHttp error) {
            ManejadorApi.responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } finally {
            intercambio.close();
        }
    }
}
