package gestor.entrada;

import gestor.modelo.AlgoritmoPlanificacion;
import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.DefinicionProceso;
import gestor.modelo.EventoES;
import gestor.modelo.PoliticaMemoria;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Contrato completo de config-json.md y batería SC-002 de archivos inválidos. */
public class LectorEscenarioPrueba {
    private static final String PROCESO = "{\"id\":\"P1\",\"tiempoLlegada\":0,\"tiempoRafaga\":5,\"memoria\":20}";
    private static final String UBICACION = "procesos[0] (id \"P1\")";

    @Prueba
    public void cargaElArchivoCompletoConTodosSusValoresExactos() {
        var escenario = new LectorEscenario().leer(recurso("validos/completo.json"));
        Afirmar.iguales(new ConfiguracionSimulacion(128, 2, 3, PoliticaMemoria.FIRST_FIT,
                AlgoritmoPlanificacion.ROUND_ROBIN), escenario.configuracion(), "Configuración completa exacta");
        Afirmar.iguales(List.of(new DefinicionProceso("P1", 0, 6, 60, -2,
                List.of(new EventoES(2, 2), new EventoES(4, 1)), 0),
                new DefinicionProceso("P2", 1, 3, 40, 4, List.of(), 1)), escenario.procesos(),
                "Se cargan prioridad, E/S y orden de archivo de ambos procesos");
    }

    @Prueba
    public void aplicaLosDefectosDelArchivoMinimo() {
        var escenario = new LectorEscenario().leer(recurso("validos/minimo.json"));
        Afirmar.iguales(ConfiguracionSimulacion.porDefecto(), escenario.configuracion(), "Todos los defectos del contrato");
        Afirmar.iguales(List.of(new DefinicionProceso("P1", 0, 1, 1, null, List.of(), 0)), escenario.procesos(),
                "La prioridad ausente es nula y los bloqueos ausentes son una lista vacía");
    }

    @Prueba
    public void cadaArchivoInvalidoInformaLaUbicacionYLaCausaExactas() {
        String[][] casos = {
            {"json-mal-formado", "línea 3, columna 1: se esperaba un valor JSON"},
            {"procesos-vacio", "procesos: la lista de procesos está vacía"},
            {"sin-procesos", "procesos: campo obligatorio ausente"},
            {"campo-faltante", UBICACION + ".tiempoRafaga: campo obligatorio ausente"},
            {"tipo-invalido", UBICACION + ".memoria: debe ser un entero"},
            {"llegada-negativa", UBICACION + ".tiempoLlegada: debe ser mayor o igual a 0"},
            {"rafaga-cero", UBICACION + ".tiempoRafaga: debe ser mayor o igual a 1"},
            {"id-vacio", "procesos[0] (id \"\").id: no puede estar vacío"},
            {"id-duplicado", "procesos[1] (id \"P1\"): el id \"P1\" está duplicado"},
            {"memoria-excede", UBICACION + ".memoria: 2048 MB excede la memoria total (1024 MB); el proceso nunca podría alojarse"},
            {"enquantum-fuera-rango", UBICACION + ".bloqueosES[0].enQuantum: debe estar entre 1 y 4"},
            {"enquantum-no-creciente", UBICACION + ".bloqueosES[1].enQuantum: debe ser mayor que el anterior (2)"},
            {"duracion-cero", UBICACION + ".bloqueosES[0].duracion: debe ser mayor o igual a 1"},
            {"quantum-cero", "configuracion.quantum: debe ser mayor o igual a 1"},
            {"max-adelantamientos-cero", "configuracion.maxAdelantamientos: debe ser mayor o igual a 1"},
            {"politica-desconocida", "configuracion.politicaMemoria: valor \"WORST_FIT\" no admitido; valores válidos: BEST_FIT, FIRST_FIT"},
            {"planificador-desconocido", "configuracion.planificador: valor \"FCFS\" no admitido; valores válidos: ROUND_ROBIN"}
        };
        for (String[] caso : casos) {
            ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class,
                    () -> new LectorEscenario().leer(recurso("invalidos/" + caso[0] + ".json")));
            Afirmar.iguales(List.of("Error en config.json: " + caso[1]), error.errores(), "Diagnóstico exacto de " + caso[0]);
            Afirmar.iguales(error.errores().getFirst(), error.getMessage(), "Un error individual conserva su mensaje");
        }
    }

    @Prueba
    public void acumulaErroresDeConfiguracionYDeDistintosProcesos() {
        String json = "{\"configuracion\":{\"quantum\":0},\"procesos\":["
                + PROCESO.replace("\"memoria\":20", "\"memoria\":-1") + ","
                + PROCESO.replace("P1", "P2").replace("\"tiempoLlegada\":0", "\"tiempoLlegada\":-2") + "]}";
        ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(json));
        Afirmar.iguales(List.of("Error en config.json: configuracion.quantum: debe ser mayor o igual a 1",
                "Error en config.json: " + UBICACION + ".memoria: debe ser mayor o igual a 1",
                "Error en config.json: procesos[1] (id \"P2\").tiempoLlegada: debe ser mayor o igual a 0"),
                error.errores(), "Se informan todos los errores independientes en orden");
        Afirmar.iguales(String.join(System.lineSeparator(), error.errores()), error.getMessage(), "Los errores se unen con saltos");
        Afirmar.lanza(UnsupportedOperationException.class, () -> error.errores().clear());
    }

    @Prueba
    public void advierteCamposDesconocidosEnTodosLosNivelesSinRechazarlos() {
        var lector = new LectorEscenario();
        String json = "{\"extra\":true,\"configuracion\":{\"extra\":0},\"procesos\":["
                + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"extra\":null,"
                        + "\"bloqueosES\":[{\"enQuantum\":1,\"duracion\":1,\"extra\":2}]") + "]}";
        Afirmar.iguales(1, lector.leerTexto(json).procesos().size(), "Los desconocidos no impiden cargar el escenario");
        List<String> advertencias = lector.advertencias();
        Afirmar.iguales(4, advertencias.size(), "Hay una advertencia por campo desconocido");
        for (String ruta : List.of("extra", "configuracion.extra", UBICACION + ".extra", UBICACION + ".bloqueosES[0].extra")) {
            Afirmar.verdadero(advertencias.contains("Advertencia en config.json: " + ruta + ": campo desconocido; se ignora"),
                    "La advertencia identifica la ruta completa: " + ruta);
        }
        Afirmar.lanza(UnsupportedOperationException.class, advertencias::clear);
        lector.leerTexto("{\"procesos\":[" + PROCESO + "]}");
        Afirmar.iguales(List.of(), lector.advertencias(), "Una nueva lectura no conserva advertencias anteriores");
        Afirmar.iguales(4, advertencias.size(), "Las consultas anteriores de advertencias son estables");
    }

    @Prueba
    public void unArchivoInexistenteProduceErrorConfiguracion() {
        Path ruta = recurso("no-existe.json");
        ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leer(ruta));
        Afirmar.iguales("Error en config.json: no se encontró el archivo " + ruta, error.getMessage(), "Ruta ausente exacta");
    }

    @Prueba
    public void exigeObjetosYArreglosEnLasUbicacionesDelContrato() {
        String[][] casos = {
            {"[]", "raíz: debe ser un objeto"},
            {"{\"configuracion\":null,\"procesos\":[" + PROCESO + "]}", "configuracion: debe ser un objeto"},
            {"{\"procesos\":{}}", "procesos: debe ser un arreglo"},
            {"{\"procesos\":[null]}", "procesos[0]: debe ser un objeto"},
            {"{\"procesos\":[" + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"bloqueosES\":null") + "]}",
                    UBICACION + ".bloqueosES: debe ser un arreglo"},
            {"{\"procesos\":[" + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"bloqueosES\":[false]") + "]}",
                    UBICACION + ".bloqueosES[0]: debe ser un objeto"}
        };
        for (String[] caso : casos) {
            ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(caso[0]));
            Afirmar.contiene(error.getMessage(), caso[1], "Tipos estructurales exactos");
        }
    }

    @Prueba
    public void rechazaNulosDecimalesBooleanosYCadenasEnCamposEnteros() {
        for (String valor : List.of("null", "1.0", "1e0", "true", "\"1\"")) {
            for (String campo : List.of("tiempoLlegada", "tiempoRafaga", "memoria")) {
                String json = "{\"procesos\":[" + PROCESO.replaceFirst("\"" + campo + "\":\\d+", "\"" + campo + "\":" + valor) + "]}";
                ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(json));
                Afirmar.contiene(error.getMessage(), UBICACION + "." + campo + ": debe ser un entero", "Tipo entero estricto");
            }
        }
    }

    @Prueba
    public void validaConfiguracionParcialRangosYTiposDeTodosLosParametros() {
        var escenario = new LectorEscenario().leerTexto("{\"configuracion\":{\"quantum\":3},\"procesos\":[" + PROCESO + "]}");
        Afirmar.iguales(ConfiguracionSimulacion.porDefecto().conQuantum(3), escenario.configuracion(), "Solo cambia el campo presente");
        for (String campo : List.of("memoriaTotal", "quantum", "maxAdelantamientos")) {
            for (String valor : List.of("0", "-1", "null", "1.0", "2147483648")) {
                String json = "{\"configuracion\":{\"" + campo + "\":" + valor + "},\"procesos\":[" + PROCESO + "]}";
                ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(json));
                Afirmar.contiene(error.getMessage(), "configuracion." + campo, "El error identifica el parámetro");
            }
        }
        for (String campo : List.of("politicaMemoria", "planificador")) {
            for (String valor : List.of("null", "3", "true", "\"desconocido\"", "\"round_robin\"")) {
                String json = "{\"configuracion\":{\"" + campo + "\":" + valor + "},\"procesos\":[" + PROCESO + "]}";
                Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(json));
            }
        }
    }

    @Prueba
    public void validaIdentificadoresCamposObligatoriosYPrioridadEntera() {
        for (String campo : List.of("id", "tiempoLlegada", "tiempoRafaga", "memoria")) {
            String contenido = PROCESO.replaceAll("\"" + campo + "\":(?:\"P1\"|\\d+),?", "").replace(",}", "}");
            ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class,
                    () -> new LectorEscenario().leerTexto("{\"procesos\":[" + contenido + "]}"));
            Afirmar.contiene(error.getMessage(), "." + campo + ": campo obligatorio ausente", "Obligatoriedad de " + campo);
        }
        for (String id : List.of("null", "1", "\"   \"")) {
            Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(
                    "{\"procesos\":[" + PROCESO.replace("\"P1\"", id) + "]}"));
        }
        for (String prioridad : List.of("-2147483648", "2147483647")) {
            var escenario = new LectorEscenario().leerTexto("{\"procesos\":["
                    + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"prioridad\":" + prioridad) + "]}");
            Afirmar.iguales(Integer.valueOf(prioridad), escenario.procesos().getFirst().prioridad(), "Prioridad sin restricción de signo");
        }
        for (String prioridad : List.of("null", "2.5", "2147483648")) {
            Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto("{\"procesos\":["
                    + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"prioridad\":" + prioridad) + "]}"));
        }
    }

    @Prueba
    public void validaTodosLosCamposDeEntradaSalidaYElOrdenEstricto() {
        for (String evento : List.of("{}", "{\"enQuantum\":0,\"duracion\":1}", "{\"enQuantum\":-1,\"duracion\":1}",
                "{\"enQuantum\":1.0,\"duracion\":1}", "{\"enQuantum\":1,\"duracion\":null}",
                "{\"enQuantum\":1,\"duracion\":-1}", "{\"enQuantum\":2147483648,\"duracion\":1}")) {
            String json = "{\"procesos\":[" + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"bloqueosES\":[" + evento + "]") + "]}";
            Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(json));
        }
        String eventos = "[{\"enQuantum\":3,\"duracion\":1},{\"enQuantum\":2,\"duracion\":1}]";
        String json = "{\"procesos\":[" + PROCESO.replace("\"memoria\":20", "\"memoria\":20,\"bloqueosES\":" + eventos) + "]}";
        ErrorConfiguracion error = Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leerTexto(json));
        Afirmar.contiene(error.getMessage(), ".bloqueosES[1].enQuantum: debe ser mayor que el anterior (3)", "La secuencia no puede decrecer");
    }

    @Prueba
    public void rechazaArchivosQueNoEstanCodificadosEnUtf8() throws Exception {
        Path archivo = Files.createTempFile("gestor-codificacion-", ".json");
        try {
            Files.write(archivo, new byte[] {(byte) 0xc3, (byte) 0x28});
            Afirmar.lanza(ErrorConfiguracion.class, () -> new LectorEscenario().leer(archivo));
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    private Path recurso(String nombre) {
        return Path.of("build", "pruebas", "recursos", nombre);
    }
}
