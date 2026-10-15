package gestor;

import gestor.entrada.ErrorConfiguracion;
import gestor.entrada.LectorEscenario;
import gestor.interfaz.web.ServidorWeb;
import gestor.modelo.Escenario;
import gestor.motor.ControladorSimulacion;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Punto de entrada: argumentos, carga del escenario y arranque del servidor local. */
public final class Principal {
    private Principal() {
    }

    public static void main(String[] argumentos) {
        Argumentos opciones;
        try {
            opciones = interpretar(argumentos);
        } catch (IllegalArgumentException excepcion) {
            System.err.println("Error de uso: " + excepcion.getMessage());
            System.err.println("Uso: java -jar build/gestor-procesos.jar [--config <ruta>] [--puerto <0–65535>]");
            System.exit(1);
            return;
        }

        ControladorSimulacion controlador = null;
        try {
            controlador = new ControladorSimulacion(() -> cargar(opciones.rutaConfiguracion()));
            var estado = controlador.instantanea();
            var configuracion = estado.configuracion();
            System.out.println("Escenario cargado: " + estado.procesos().size() + " procesos, RAM "
                    + configuracion.memoriaTotal() + " MB, quantum " + configuracion.quantum()
                    + ", política " + configuracion.politicaMemoria().etiqueta()
                    + ", planificador " + configuracion.planificador().etiqueta());
            var servidor = new ServidorWeb(controlador, opciones.puerto());
            var controlAlCerrar = controlador;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                controlAlCerrar.detener();
                servidor.detener();
            }, "cierre-simulador"));
            servidor.iniciar();
            System.out.println("Simulador disponible en http://localhost:" + servidor.puerto());
        } catch (ErrorConfiguracion excepcion) {
            for (String error : excepcion.errores()) {
                System.err.println(error);
            }
            System.exit(1);
        } catch (IOException excepcion) {
            if (controlador != null) controlador.detener();
            System.err.println("Error al iniciar el servidor: " + excepcion.getMessage());
            System.exit(1);
        }
    }

    private static Escenario cargar(Path ruta) {
        var lector = new LectorEscenario();
        try { return lector.leer(ruta); }
        finally { imprimirAdvertencias(lector); }
    }

    private static void imprimirAdvertencias(LectorEscenario lector) {
        for (String advertencia : lector.advertencias()) {
            System.err.println(advertencia);
        }
    }

    private static Argumentos interpretar(String[] argumentos) {
        Path ruta = Path.of("config.json");
        int puerto = 8080;
        for (int indice = 0; indice < argumentos.length; indice++) {
            String opcion = argumentos[indice];
            if (!opcion.equals("--config") && !opcion.equals("--puerto")) {
                throw new IllegalArgumentException("argumento no reconocido: " + opcion);
            }
            if (indice + 1 == argumentos.length || argumentos[indice + 1].startsWith("--")) {
                throw new IllegalArgumentException("falta el valor de " + opcion);
            }
            String valor = argumentos[++indice];
            if (opcion.equals("--config")) {
                try {
                    ruta = Path.of(valor);
                } catch (InvalidPathException excepcion) {
                    throw new IllegalArgumentException("la ruta de --config no es válida");
                }
            } else {
                try {
                    puerto = Integer.parseInt(valor);
                } catch (NumberFormatException excepcion) {
                    throw new IllegalArgumentException("--puerto debe ser un entero entre 0 y 65535");
                }
                if (puerto < 0 || puerto > 65535) {
                    throw new IllegalArgumentException("--puerto debe ser un entero entre 0 y 65535");
                }
            }
        }
        return new Argumentos(ruta, puerto);
    }

    private record Argumentos(Path rutaConfiguracion, int puerto) {}
}
