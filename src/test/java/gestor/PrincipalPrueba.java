package gestor;

import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Verifica códigos y canales de salida sin ejecutar System.exit dentro del ejecutor de pruebas. */
public class PrincipalPrueba {
    @Prueba
    public void cargaLaConfiguracionPorDefectoYDejaLaApiDisponible() throws Exception {
        Resultado resultado = ejecutar("--puerto", "0");
        Afirmar.nulo(resultado.codigo(), "El servidor permanece activo tras validar config.json");
        Afirmar.contiene(resultado.salida(), "Escenario cargado: 6 procesos, RAM 120 MB, quantum 2",
                "El resumen informa el escenario didáctico");
        Afirmar.iguales("", resultado.errores(), "El archivo por defecto no tiene errores ni advertencias");
    }

    @Prueba
    public void aceptaPuertosCeroYMaximoYRutasConEspaciosYTildes() throws Exception {
        Path ruta = Files.createTempFile("gestor escenario á-", ".json");
        try {
            Files.copy(Path.of("src/test/recursos/validos/completo.json"), ruta, StandardCopyOption.REPLACE_EXISTING);
            for (String puerto : List.of("0", "65535")) {
                Resultado resultado = ejecutar("--config", ruta.toString(), "--puerto", puerto);
                Afirmar.nulo(resultado.codigo(), "El servidor arranca con un puerto dentro de 0–65535");
                Afirmar.contiene(resultado.salida(), "Escenario cargado: 2 procesos, RAM 128 MB, quantum 2, política First-Fit, planificador Round Robin",
                        "El resumen conserva las tildes y las etiquetas");
            }
        } finally {
            Files.deleteIfExists(ruta);
        }
    }

    @Prueba
    public void unEscenarioInvalidoSaleConUnoYDiagnosticoEnElCanalDeErrores() throws Exception {
        Resultado resultado = ejecutar("--config", "src/test/recursos/invalidos/id-duplicado.json");
        Afirmar.iguales(1, resultado.codigo(), "No se arranca con un escenario inválido");
        Afirmar.contiene(resultado.errores(), "Error en config.json: procesos[1] (id \"P1\"): el id \"P1\" está duplicado",
                "El error de configuración se imprime en español en stderr");
        Afirmar.iguales("", resultado.salida(), "No se informa una carga correcta cuando falla la validación");
    }

    @Prueba
    public void rechazaArgumentosDesconocidosIncompletosYPuertosInvalidos() throws Exception {
        for (List<String> argumentos : List.of(List.of("--config"), List.of("--config", "--puerto"),
                List.of("--puerto"), List.of("--otro"),
                List.of("--puerto", "-1"), List.of("--puerto", "65536"), List.of("--puerto", "abc"),
                List.of("--puerto", "1.5"), List.of("--puerto", "2147483648"))) {
            Resultado resultado = ejecutar(argumentos.toArray(String[]::new));
            Afirmar.iguales(1, resultado.codigo(), "Los argumentos inválidos terminan con código 1");
            Afirmar.contiene(resultado.errores(), "Error de uso:", "El error de argumentos se informa en español");
            Afirmar.contiene(resultado.errores(), "Uso:", "Se indica cómo invocar el programa");
        }
    }

    @Prueba
    public void imprimeAdvertenciasSinConvertirlasEnErrores() throws Exception {
        Path ruta = Files.createTempFile("gestor-advertencia-", ".json");
        try {
            String texto = Files.readString(Path.of("src/test/recursos/validos/minimo.json"), StandardCharsets.UTF_8);
            Files.writeString(ruta, texto.replaceFirst("\\{", "{\"extra\":true,"), StandardCharsets.UTF_8);
            Resultado resultado = ejecutar("--config", ruta.toString(), "--puerto", "0");
            Afirmar.nulo(resultado.codigo(), "Una advertencia no impide arrancar el servidor");
            Afirmar.contiene(resultado.errores(), "Advertencia en config.json: extra: campo desconocido; se ignora",
                    "La advertencia se muestra en consola");
            Afirmar.contiene(resultado.salida(), "Escenario cargado: 1 procesos", "Se informa la carga correcta");
            Files.writeString(ruta, texto.replaceFirst("\\{", "{\"extra\":true,\"configuracion\":{\"quantum\":0},"),
                    StandardCharsets.UTF_8);
            Resultado invalido = ejecutar("--config", ruta.toString());
            Afirmar.iguales(1, invalido.codigo(), "La advertencia no oculta un error real");
            Afirmar.contiene(invalido.errores(), "Advertencia en config.json: extra:",
                    "Las advertencias también se imprimen cuando hay errores semánticos");
            Afirmar.contiene(invalido.errores(), "Error en config.json: configuracion.quantum:",
                    "El error y la advertencia se conservan juntos");
        } finally {
            Files.deleteIfExists(ruta);
        }
    }

    @Prueba
    public void unPuertoOcupadoSaleConUnoYMensajeEnEspanol() throws Exception {
        try (var reserva = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            Resultado resultado = ejecutar("--puerto", Integer.toString(reserva.getLocalPort()));
            Afirmar.iguales(1, resultado.codigo(), "Fallo de enlace termina con código 1");
            Afirmar.contiene(resultado.errores(), "El puerto " + reserva.getLocalPort() + " ya está en uso", "Diagnóstico del puerto ocupado");
        }
    }

    private Resultado ejecutar(String... argumentos) throws Exception {
        String ejecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var comando = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", ejecutable).toString(),
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                "gestor.Principal"));
        comando.addAll(List.of(argumentos));
        Process proceso = new ProcessBuilder(comando).start();
        StringBuffer salida = new StringBuffer();
        var url = new java.util.concurrent.atomic.AtomicReference<String>();
        Thread lector = Thread.ofPlatform().daemon().start(() -> {
            try (var lineas = proceso.inputReader(StandardCharsets.UTF_8)) {
                String linea;
                while ((linea = lineas.readLine()) != null) {
                    salida.append(linea).append(System.lineSeparator());
                    if (linea.startsWith("Simulador disponible en ")) url.set(linea.substring("Simulador disponible en ".length()));
                }
            } catch (java.io.IOException excepcion) {
                // La destrucción del subproceso puede cerrar el conducto después de las verificaciones.
            }
        });
        try {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while (proceso.isAlive() && url.get() == null && System.nanoTime() < limite) Thread.sleep(10);
            Afirmar.verdadero(!proceso.isAlive() || url.get() != null, "El proceso informa una URL o un error dentro del plazo");
            Integer codigo = proceso.isAlive() ? null : proceso.exitValue();
            if (url.get() != null) {
                try (var cliente = java.net.http.HttpClient.newHttpClient()) {
                    var respuesta = cliente.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url.get() + "/api/estado"))
                            .timeout(java.time.Duration.ofSeconds(3)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    Afirmar.iguales(200, respuesta.statusCode(), "La URL anunciada sirve la API");
                }
            }
            proceso.destroy();
            Afirmar.verdadero(proceso.waitFor(3, TimeUnit.SECONDS), "El proceso libera servidor y reloj al cerrar");
            lector.join(1000);
            return new Resultado(codigo, salida.toString(), new String(proceso.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            proceso.destroyForcibly();
        }
    }

    private record Resultado(Integer codigo, String salida, String errores) {}
}
