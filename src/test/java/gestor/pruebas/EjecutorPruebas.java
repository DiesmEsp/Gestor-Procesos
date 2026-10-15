package gestor.pruebas;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/** Ejecuta los métodos anotados de las clases recibidas por la línea de comandos. */
public final class EjecutorPruebas {
    private EjecutorPruebas() {
    }

    public static void main(String[] nombresClases) {
        int pruebas = 0;
        int fallidas = 0;

        for (String nombreClase : nombresClases) {
            try {
                Class<?> clase = Class.forName(nombreClase);
                Method[] metodos = clase.getDeclaredMethods();
                Arrays.sort(metodos, Comparator.comparing(Method::getName));

                for (Method metodo : metodos) {
                    if (!metodo.isAnnotationPresent(Prueba.class)) {
                        continue;
                    }
                    pruebas++;
                    String nombrePrueba = clase.getSimpleName() + "." + metodo.getName();
                    try {
                        Constructor<?> constructor = clase.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        Object instancia = constructor.newInstance();
                        metodo.setAccessible(true);
                        metodo.invoke(instancia);
                        System.out.println("[OK] " + nombrePrueba);
                    } catch (InvocationTargetException excepcion) {
                        fallidas++;
                        informarFallo(nombrePrueba, excepcion.getCause());
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError excepcion) {
                        fallidas++;
                        informarFallo(nombrePrueba, excepcion);
                    }
                }
            } catch (ClassNotFoundException | RuntimeException | LinkageError excepcion) {
                pruebas++;
                fallidas++;
                informarFallo(nombreClase, excepcion);
            }
        }

        System.out.println(pruebas + " pruebas, " + fallidas + " fallidas");
        if (fallidas > 0) {
            System.exit(1);
        }
    }

    private static void informarFallo(String nombre, Throwable excepcion) {
        String mensaje = excepcion.getMessage();
        if (mensaje == null) {
            mensaje = "Excepción de tipo " + excepcion.getClass().getSimpleName() + " sin mensaje";
        }
        System.out.println("[FALLO] " + nombre + ": " + mensaje);
    }
}
