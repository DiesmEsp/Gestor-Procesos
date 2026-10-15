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
    public void cargaLaConfiguracionPorDefectoYTerminaCorrectamente() throws Exception {
        Resultado resultado = ejecutar();
        Afirmar.iguales(0, resultado.codigo(), "El config.json por defecto es válido");
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
                Afirmar.iguales(0, resultado.codigo(), "El puerto está dentro de 0–65535");
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
            Resultado resultado = ejecutar("--config", ruta.toString());
            Afirmar.iguales(0, resultado.codigo(), "Una advertencia no impide cargar el escenario");
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

    private Resultado ejecutar(String... argumentos) throws Exception {
        String ejecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var comando = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", ejecutable).toString(),
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                "gestor.Principal"));
        comando.addAll(List.of(argumentos));
        Process proceso = new ProcessBuilder(comando).start();
        try {
            Afirmar.verdadero(proceso.waitFor(5, TimeUnit.SECONDS), "El arranque informativo debe finalizar sin esperar un servidor");
            return new Resultado(proceso.exitValue(), new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                    new String(proceso.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            proceso.destroyForcibly();
        }
    }

    private record Resultado(int codigo, String salida, String errores) {}
}
