package gestor.interfaz.web;

import gestor.json.LectorJson;
import gestor.modelo.*;
import gestor.motor.*;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static gestor.pruebas.EscenariosMotor.definir;

public class ApiHttpPrueba {
    @Prueba
    public void estadoInicialTieneElContratoCompletoYUtf8() throws Exception {
        try (var sesion = new Sesion("P1")) {
            var respuesta = sesion.pedir("GET", "/api/estado", null);
            Afirmar.iguales(200, respuesta.statusCode(), "Consulta disponible");
            Afirmar.iguales("application/json; charset=utf-8", respuesta.headers().firstValue("Content-Type").orElseThrow(), "JSON UTF-8");
            var estado = objeto(respuesta.body());
            Afirmar.iguales(Set.of("instante", "modo", "configuracion", "colas", "procesos", "memoria", "gantt", "bitacora", "totalEventos", "metricas", "aviso"), estado.keySet(), "Campos raíz exactos");
            Afirmar.iguales("DETENIDA", estado.get("modo"), "Modo serializado");
            var configuracion = mapa(estado.get("configuracion"));
            Afirmar.iguales(Set.of("memoriaTotal", "quantum", "maxAdelantamientos", "velocidadMs", "politicaMemoria", "planificador"), configuracion.keySet(), "Campos de configuración");
            Afirmar.iguales("ROUND_ROBIN", configuracion.get("planificador"), "Planificador informativo");
            Afirmar.iguales(500L, configuracion.get("velocidadMs"), "Velocidad inicial");
            Afirmar.iguales(Set.of("nuevos", "listos", "ejecucion", "bloqueados", "terminados"), mapa(estado.get("colas")).keySet(), "Todas las colas");
            var proceso = mapa(((List<?>) estado.get("procesos")).getFirst());
            Afirmar.iguales(Set.of("id", "estado", "tiempoLlegada", "tiempoRafaga", "ticksConsumidos", "ticksRestantes", "memoria", "prioridad", "bloque", "tiempoEspera", "ticksES", "adelantamientos", "bloqueante", "ticksRestantesES", "instanteFinalizacion", "tiempoRetorno"), proceso.keySet(), "Campos de proceso exactos");
            Afirmar.nulo(proceso.get("bloque"), "Pendiente sin bloque");
            Afirmar.nulo(mapa(estado.get("metricas")).get("utilizacionCpu"), "Métrica inicial nula");
            Afirmar.iguales(Set.of("total", "libre", "mayorHueco", "fragmentacionExterna", "bloques"), mapa(estado.get("memoria")).keySet(), "Memoria completa");
        }
    }

    @Prueba
    public void bitacoraIncrementalConservaTotalYToleraIndicesPosterioresAlReinicio() throws Exception {
        try (var sesion = new Sesion("P1")) {
            sesion.control.iniciar();
            sesion.control.avanzarManual();
            var completa = objeto(sesion.pedir("GET", "/api/estado", null).body());
            List<?> eventos = (List<?>) completa.get("bitacora");
            Afirmar.verdadero(eventos.size() >= 3, "Llegada, admisión y despacho");
            var parcial = objeto(sesion.pedir("GET", "/api/estado?desdeEvento=1", null).body());
            Afirmar.iguales(eventos.subList(1, eventos.size()), parcial.get("bitacora"), "Sufijo por índice, no por instante");
            Afirmar.iguales((long) eventos.size(), parcial.get("totalEventos"), "Total completo");
            Afirmar.iguales(completa.get("gantt"), parcial.get("gantt"), "Gantt siempre completo");
            Afirmar.iguales(List.of(), objeto(sesion.pedir("GET", "/api/estado?desdeEvento=9999999999", null).body()).get("bitacora"), "Índice mayor que el total");
            sesion.control.reiniciar();
            var reiniciada = objeto(sesion.pedir("GET", "/api/estado?desdeEvento=3", null).body());
            Afirmar.iguales(0L, reiniciada.get("totalEventos"), "El total vuelve a cero");
            Afirmar.iguales(List.of(), reiniciada.get("bitacora"), "Consulta incremental anterior al reinicio");
        }
    }

    @Prueba
    public void rechazaDesdeEventoInvalido() throws Exception {
        try (var sesion = new Sesion("P1")) {
            for (String valor : List.of("-1", "abc", "1.5", "", "1&desdeEvento=2")) {
                error(sesion.pedir("GET", "/api/estado?desdeEvento=" + valor, null), 400, "VALOR_INVALIDO");
            }
        }
    }

    @Prueba
    public void consultaProcesosEnCualquierEstadoYDevuelve404ConId() throws Exception {
        try (var sesion = new Sesion("P1")) {
            var pendiente = objeto(sesion.pedir("GET", "/api/proceso/P1", null).body());
            Afirmar.iguales("PENDIENTE_LLEGADA", pendiente.get("estado"), "Pendiente consultable");
            Afirmar.iguales(0L, pendiente.get("instante"), "Instante de la consulta");
            sesion.control.iniciar();
            sesion.control.avanzarManual();
            var activo = objeto(sesion.pedir("GET", "/api/proceso/P1", null).body());
            Afirmar.iguales(Set.of("base", "limite", "tamano"), mapa(activo.get("bloque")).keySet(), "Bloque por proceso sin campos extra");
            while (sesion.control.instantanea().modo() == ModoSimulacion.EN_CURSO) sesion.control.avanzarManual();
            var terminado = objeto(sesion.pedir("GET", "/api/proceso/P1", null).body());
            Afirmar.iguales("TERMINADO", terminado.get("estado"), "Terminado consultable");
            Afirmar.iguales(3L, terminado.get("tiempoRetorno"), "Métrica final");
            var ausente = sesion.pedir("GET", "/api/proceso/P9", null);
            error(ausente, 404, "PROCESO_NO_EXISTE");
            Afirmar.iguales("El proceso 'P9' no existe", objeto(ausente.body()).get("mensaje"), "Mensaje contractual");
        }
    }

    @Prueba
    public void preservaIdsConTildesEspaciosBarrasYMas() throws Exception {
        String id = "Proceso á +/1";
        try (var sesion = new Sesion(id)) {
            String segmento = URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
            var respuesta = sesion.pedir("GET", "/api/proceso/" + segmento, null);
            Afirmar.iguales(200, respuesta.statusCode(), "Id codificado en la ruta");
            Afirmar.iguales(id, objeto(respuesta.body()).get("id"), "Id recuperado sin pérdidas");
        }
    }

    @Prueba
    public void lasAccionesHttpRespetanTransicionesYReinicio() throws Exception {
        try (var sesion = new Sesion("P1")) {
            error(sesion.pedir("POST", "/api/control/pausar", ""), 409, "TRANSICION_INVALIDA");
            Afirmar.iguales("EN_CURSO", objeto(sesion.pedir("POST", "/api/control/iniciar", "").body()).get("modo"), "Inicio");
            error(sesion.pedir("POST", "/api/control/iniciar", ""), 409, "TRANSICION_INVALIDA");
            sesion.control.avanzarManual();
            Afirmar.iguales("PAUSADA", objeto(sesion.pedir("POST", "/api/control/pausar", "").body()).get("modo"), "Pausa");
            sesion.pedir("POST", "/api/control/iniciar", "");
            while (sesion.control.instantanea().modo() == ModoSimulacion.EN_CURSO) sesion.control.avanzarManual();
            error(sesion.pedir("POST", "/api/control/iniciar", ""), 409, "TRANSICION_INVALIDA");
            var reinicio = objeto(sesion.pedir("POST", "/api/control/reiniciar", "").body());
            Afirmar.iguales("DETENIDA", reinicio.get("modo"), "Reinicio");
            Afirmar.iguales(0L, reinicio.get("instante"), "Reloj reiniciado");
        }
    }

    @Prueba
    public void configuracionValidaSeAplicaCompleta() throws Exception {
        try (var sesion = new Sesion("P1")) {
            var respuesta = sesion.pedir("POST", "/api/configuracion", "{\"quantum\":4,\"memoriaTotal\":120,\"velocidadMs\":50,\"politicaMemoria\":\"FIRST_FIT\"}");
            Afirmar.iguales(200, respuesta.statusCode(), "Configuración aceptada");
            var configuracion = mapa(objeto(respuesta.body()).get("configuracion"));
            Afirmar.iguales(4L, configuracion.get("quantum"), "Quantum");
            Afirmar.iguales(120L, configuracion.get("memoriaTotal"), "RAM");
            Afirmar.iguales(50L, configuracion.get("velocidadMs"), "Velocidad");
            Afirmar.iguales("FIRST_FIT", configuracion.get("politicaMemoria"), "Política");
            Afirmar.iguales("ROUND_ROBIN", configuracion.get("planificador"), "Planificador fijo");
        }
    }

    @Prueba
    public void valoresInvalidosNoAplicanNingunCampo() throws Exception {
        try (var sesion = new Sesion("P1")) {
            var inicial = sesion.control.instantanea();
            for (String cuerpo : List.of("{", "null", "[]", "", "{\"quantum\":0}", "{\"quantum\":1.0}",
                    "{\"memoriaTotal\":null}", "{\"quantum\":true}", "{\"quantum\":\"2\"}",
                    "{\"quantum\":2147483648}", "{\"quantum\":4,\"velocidadMs\":49}",
                    "{\"quantum\":4,\"velocidadMs\":2001}", "{\"quantum\":4,\"politicaMemoria\":\"DESCONOCIDA\"}",
                    "{\"politicaMemoria\":\"first_fit\"}", "{\"planificador\":\"ROUND_ROBIN\"}", "{\"otro\":3}")) {
                error(sesion.pedir("POST", "/api/configuracion", cuerpo), 400, "VALOR_INVALIDO");
                Afirmar.iguales(inicial, sesion.control.instantanea(), "Validación atómica de " + cuerpo);
            }
        }
    }

    @Prueba
    public void conflictosDeMemoriaNoAplicanOtrosCambios() throws Exception {
        try (var sesion = new Sesion("P1")) {
            var insuficiente = sesion.pedir("POST", "/api/configuracion", "{\"quantum\":4,\"memoriaTotal\":9}");
            error(insuficiente, 409, "MEMORIA_INSUFICIENTE");
            Afirmar.contiene((String) objeto(insuficiente.body()).get("mensaje"), "P1", "Identifica proceso");
            Afirmar.iguales(2, sesion.control.instantanea().configuracion().quantum(), "Quantum sin cambios");
            sesion.control.iniciar();
            for (boolean pausa : List.of(false, true)) {
                if (pausa) sesion.control.pausar();
                for (String campo : List.of("\"memoriaTotal\":120", "\"politicaMemoria\":\"FIRST_FIT\"")) {
                    error(sesion.pedir("POST", "/api/configuracion", "{\"quantum\":4," + campo + "}"), 409, "MEMORIA_NO_EDITABLE");
                    Afirmar.iguales(2, sesion.control.instantanea().configuracion().quantum(), "Conflicto no aplica quantum");
                }
            }
            Afirmar.iguales(200, sesion.pedir("POST", "/api/configuracion", "{\"quantum\":3,\"velocidadMs\":2000}").statusCode(), "Quantum y velocidad editables tras iniciar");
        }
    }

    @Prueba
    public void rechazaMetodosYEncuentraSoloRutasExactas() throws Exception {
        try (var sesion = new Sesion("P1")) {
            for (String ruta : List.of("/api/estado", "/api/proceso/P1")) error(sesion.pedir("POST", ruta, "{}"), 405, "METODO_NO_PERMITIDO");
            for (String ruta : List.of("/api/control/iniciar", "/api/control/pausar", "/api/control/reiniciar", "/api/configuracion")) error(sesion.pedir("GET", ruta, null), 405, "METODO_NO_PERMITIDO");
            for (String ruta : List.of("/api/inexistente", "/api/estado/otro", "/api/control/inexistente", "/desconocida", "/../config.json")) {
                Afirmar.iguales(404, sesion.pedir("GET", ruta, null).statusCode(), "Ruta desconocida: " + ruta);
            }
        }
    }

    @Prueba
    public void sirveLaPaginaInicialYSoloEscuchaEnLoopback() throws Exception {
        try (var sesion = new Sesion("P1")) {
            Afirmar.verdadero(sesion.servidor.direccion().getAddress().isLoopbackAddress(), "Servidor local");
            Afirmar.verdadero(sesion.servidor.puerto() > 0, "Puerto efímero asignado");
            var respuesta = sesion.pedir("GET", "/", null);
            Afirmar.iguales(200, respuesta.statusCode(), "Página incluida en el classpath");
            Afirmar.iguales("text/html; charset=utf-8", respuesta.headers().firstValue("Content-Type").orElseThrow(), "HTML UTF-8");
            Afirmar.contiene(respuesta.body(), "lang=\"es\"", "Página en español");
            error(sesion.pedir("POST", "/", ""), 405, "METODO_NO_PERMITIDO");
            try {
                new ServidorWeb(sesion.control, sesion.servidor.puerto()).close();
                throw new AssertionError("Un puerto ocupado debe rechazarse");
            } catch (java.io.IOException excepcion) {
                Afirmar.contiene(excepcion.getMessage(), "El puerto " + sesion.servidor.puerto() + " ya está en uso", "Error de enlace en español");
            }
        }
    }

    @Prueba
    public void consultasConcurrentesDevuelvenInstantaneasCompletas() throws Exception {
        try (var sesion = new Sesion("P1")) {
            sesion.control.iniciar();
            var solicitudes = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
            for (int indice = 0; indice < 20; indice++) {
                solicitudes.add(sesion.cliente.sendAsync(HttpRequest.newBuilder(URI.create(sesion.base + "/api/estado")).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
            }
            sesion.control.avanzarManual();
            for (var solicitud : solicitudes) {
                var respuesta = solicitud.get(3, java.util.concurrent.TimeUnit.SECONDS);
                Afirmar.iguales(200, respuesta.statusCode(), "Respuesta concurrente");
                var estado = objeto(respuesta.body());
                long instante = (Long) estado.get("instante");
                var tramos = (List<?>) estado.get("gantt");
                Afirmar.iguales(instante == 0 ? 0L : mapa(tramos.getLast()).get("fin"), instante, "Gantt y reloj del mismo tick");
                Afirmar.iguales((long) ((List<?>) estado.get("bitacora")).size(), estado.get("totalEventos"), "Bitácora consistente");
            }
        }
    }

    private static void error(HttpResponse<String> respuesta, int codigo, String error) {
        Afirmar.iguales(codigo, respuesta.statusCode(), "Código HTTP: " + respuesta.body());
        var cuerpo = objeto(respuesta.body());
        Afirmar.iguales(Set.of("error", "mensaje"), cuerpo.keySet(), "Forma del error");
        Afirmar.iguales(error, cuerpo.get("error"), "Código de error");
        Afirmar.verdadero(cuerpo.get("mensaje") instanceof String, "Mensaje explicativo");
    }

    private static Map<?, ?> objeto(String texto) { return mapa(LectorJson.leer(texto)); }
    private static Map<?, ?> mapa(Object valor) { return (Map<?, ?>) valor; }

    private static final class Sesion implements AutoCloseable {
        final ControladorSimulacion control;
        final ServidorWeb servidor;
        final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        final String base;

        Sesion(String id) throws Exception {
            var escenario = new Escenario(new ConfiguracionSimulacion(100, 2, 5, PoliticaMemoria.BEST_FIT,
                    AlgoritmoPlanificacion.ROUND_ROBIN), List.of(definir(id, 0, 3, 10, 0)));
            control = new ControladorSimulacion(() -> escenario, false);
            servidor = new ServidorWeb(control, 0);
            servidor.iniciar();
            String direccion = servidor.direccion().getAddress().getHostAddress();
            base = "http://" + (direccion.contains(":") ? "[" + direccion + "]" : direccion) + ":" + servidor.puerto();
        }

        HttpResponse<String> pedir(String metodo, String ruta, String cuerpo) throws Exception {
            return cliente.send(HttpRequest.newBuilder(URI.create(base + ruta)).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        public void close() {
            servidor.close();
            control.close();
            cliente.close();
        }
    }
}
