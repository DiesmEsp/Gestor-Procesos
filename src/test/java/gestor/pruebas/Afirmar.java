package gestor.pruebas;

import java.util.Objects;

/** Aserciones del micro-framework de pruebas, sin dependencias externas. */
public final class Afirmar {
    private Afirmar() {
    }

    public static void iguales(Object esperado, Object real, String mensaje) {
        if (!Objects.equals(esperado, real)) {
            throw new AssertionError(mensaje + ": Se esperaba <" + esperado + "> pero fue <" + real + ">");
        }
    }

    public static void iguales(double esperado, double real, double tolerancia, String mensaje) {
        if (Double.isNaN(tolerancia) || tolerancia < 0) {
            throw new AssertionError(mensaje + ": La tolerancia debe ser un número no negativo");
        }
        if (Double.compare(esperado, real) != 0 && !(Math.abs(esperado - real) <= tolerancia)) {
            throw new AssertionError(mensaje + ": Se esperaba <" + esperado + "> pero fue <" + real
                    + "> con tolerancia <" + tolerancia + ">");
        }
    }

    public static void verdadero(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje + ": Se esperaba una condición verdadera");
        }
    }

    public static void falso(boolean condicion, String mensaje) {
        if (condicion) {
            throw new AssertionError(mensaje + ": Se esperaba una condición falsa");
        }
    }

    public static void nulo(Object valor, String mensaje) {
        if (valor != null) {
            throw new AssertionError(mensaje + ": Se esperaba un valor nulo pero fue <" + valor + ">");
        }
    }

    public static void noNulo(Object valor, String mensaje) {
        if (valor == null) {
            throw new AssertionError(mensaje + ": Se esperaba un valor no nulo");
        }
    }

    public static void contiene(String texto, String fragmento, String mensaje) {
        if (texto == null || fragmento == null || !texto.contains(fragmento)) {
            throw new AssertionError(mensaje + ": Se esperaba que <" + texto + "> contuviera <" + fragmento + ">");
        }
    }

    public static <T extends Throwable> T lanza(Class<T> tipoEsperado, Runnable accion) {
        try {
            accion.run();
        } catch (Throwable excepcion) {
            if (tipoEsperado.isInstance(excepcion)) {
                return tipoEsperado.cast(excepcion);
            }
            throw new AssertionError("Se esperaba una excepción de tipo <" + tipoEsperado.getName()
                    + "> pero se lanzó <" + excepcion.getClass().getName() + ">", excepcion);
        }
        throw new AssertionError("Se esperaba una excepción de tipo <" + tipoEsperado.getName()
                + "> pero no se lanzó ninguna");
    }
}
