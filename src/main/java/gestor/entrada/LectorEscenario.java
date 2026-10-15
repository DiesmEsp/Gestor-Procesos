package gestor.entrada;

import gestor.json.ErrorJson;
import gestor.json.LectorJson;
import gestor.modelo.AlgoritmoPlanificacion;
import gestor.modelo.ConfiguracionSimulacion;
import gestor.modelo.DefinicionProceso;
import gestor.modelo.Escenario;
import gestor.modelo.EventoES;
import gestor.modelo.PoliticaMemoria;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lee escenarios UTF-8 y valida el contrato completo antes de construir el modelo. */
public final class LectorEscenario {
    /**
     * Crea una instancia lista para su primer uso, sin estado previo.
     */
    public LectorEscenario() {}

    private final List<String> advertencias = new ArrayList<>();

    /**
     * Lee un archivo UTF-8 y valida todos los campos del escenario antes de construirlo.
     *
     * @param ruta ruta al archivo JSON codificado en UTF-8
     * @return el escenario validado del archivo
     * @throws ErrorConfiguracion si el archivo no es legible o contiene errores de configuración
     */
    public Escenario leer(Path ruta) {
        advertencias.clear();
        try {
            return leerTexto(Files.readString(ruta, StandardCharsets.UTF_8));
        } catch (NoSuchFileException excepcion) {
            throw new ErrorConfiguracion(List.of("Error en config.json: no se encontró el archivo " + ruta));
        } catch (IOException excepcion) {
            throw new ErrorConfiguracion(List.of("Error en config.json: no se pudo leer el archivo " + ruta
                    + "; debe ser legible y estar codificado en UTF-8"));
        }
    }

    /**
     * Valida un escenario JSON y acumula sus errores antes de devolver el modelo.
     *
     * @param json documento JSON del escenario
     * @return el escenario validado
     */
    public Escenario leerTexto(String json) {
        advertencias.clear();
        Object contenido;
        try {
            contenido = LectorJson.leer(json);
        } catch (ErrorJson excepcion) {
            throw new ErrorConfiguracion(List.of("Error en config.json: " + excepcion.getMessage()));
        }
        Validacion validacion = new Validacion();
        Escenario escenario = validacion.validar(contenido);
        advertencias.addAll(validacion.advertencias);
        if (!validacion.errores.isEmpty()) {
            throw new ErrorConfiguracion(validacion.errores);
        }
        return escenario;
    }

    /**
     * Consulta una copia de las advertencias de la última lectura.
     *
     * @return una copia de las advertencias de la última lectura
     */
    public List<String> advertencias() {
        return List.copyOf(advertencias);
    }

    /** Cada lectura tiene sus diagnósticos propios y evita construir registros con valores inválidos. */
    private static final class Validacion {
        private static final Set<String> CAMPOS_RAIZ = Set.of("configuracion", "procesos");
        private static final Set<String> CAMPOS_CONFIGURACION = Set.of("memoriaTotal", "quantum", "maxAdelantamientos",
                "politicaMemoria", "planificador");
        private static final Set<String> CAMPOS_PROCESO = Set.of("id", "tiempoLlegada", "tiempoRafaga", "memoria",
                "prioridad", "bloqueosES");
        private static final Set<String> CAMPOS_EVENTO = Set.of("enQuantum", "duracion");
        private final List<String> errores = new ArrayList<>();
        private final List<String> advertencias = new ArrayList<>();

        private Escenario validar(Object contenido) {
            Map<?, ?> raiz = objeto(contenido, "raíz");
            if (raiz == null) {
                return null;
            }
            advertirDesconocidos(raiz, CAMPOS_RAIZ, "");
            Map<?, ?> configuracion = raiz.containsKey("configuracion")
                    ? objeto(raiz.get("configuracion"), "configuracion") : Map.of();
            Integer memoria = null;
            Integer quantum = null;
            Integer maxAdelantamientos = null;
            PoliticaMemoria politica = null;
            AlgoritmoPlanificacion planificador = null;
            if (configuracion != null) {
                advertirDesconocidos(configuracion, CAMPOS_CONFIGURACION, "configuracion");
                memoria = parametroEntero(configuracion, "memoriaTotal", ConfiguracionSimulacion.MEMORIA_POR_DEFECTO);
                quantum = parametroEntero(configuracion, "quantum", ConfiguracionSimulacion.QUANTUM_POR_DEFECTO);
                maxAdelantamientos = parametroEntero(configuracion, "maxAdelantamientos",
                        ConfiguracionSimulacion.MAX_ADELANTAMIENTOS_POR_DEFECTO);
                politica = leerPolitica(configuracion);
                planificador = leerPlanificador(configuracion);
            }
            List<DefinicionProceso> procesos = leerProcesos(raiz, memoria);
            if (!errores.isEmpty()) {
                return null;
            }
            return new Escenario(new ConfiguracionSimulacion(memoria, quantum, maxAdelantamientos, politica, planificador), procesos);
        }

        private Integer parametroEntero(Map<?, ?> configuracion, String campo, int defecto) {
            if (configuracion.containsKey(campo)) {
                return entero(configuracion, campo, "configuracion." + campo, false, 1);
            }
            return defecto;
        }

        private PoliticaMemoria leerPolitica(Map<?, ?> configuracion) {
            if (!configuracion.containsKey("politicaMemoria")) {
                return ConfiguracionSimulacion.POLITICA_POR_DEFECTO;
            }
            String ruta = "configuracion.politicaMemoria";
            String codigo = texto(configuracion, "politicaMemoria", ruta, false);
            if (codigo == null) {
                return null;
            }
            var politica = PoliticaMemoria.desdeCodigo(codigo);
            if (politica.isEmpty()) {
                error(ruta, "valor \"" + codigo + "\" no admitido; valores válidos: " + PoliticaMemoria.valoresValidos());
            }
            return politica.orElse(null);
        }

        private AlgoritmoPlanificacion leerPlanificador(Map<?, ?> configuracion) {
            if (!configuracion.containsKey("planificador")) {
                return ConfiguracionSimulacion.PLANIFICADOR_POR_DEFECTO;
            }
            String ruta = "configuracion.planificador";
            String codigo = texto(configuracion, "planificador", ruta, false);
            if (codigo == null) {
                return null;
            }
            var algoritmo = AlgoritmoPlanificacion.desdeCodigo(codigo);
            if (algoritmo.isEmpty()) {
                error(ruta, "valor \"" + codigo + "\" no admitido; valores válidos: " + AlgoritmoPlanificacion.valoresValidos());
            }
            return algoritmo.orElse(null);
        }

        private List<DefinicionProceso> leerProcesos(Map<?, ?> raiz, Integer memoriaTotal) {
            List<DefinicionProceso> procesos = new ArrayList<>();
            if (!raiz.containsKey("procesos")) {
                error("procesos", "campo obligatorio ausente");
                return procesos;
            }
            List<?> elementos = arreglo(raiz.get("procesos"), "procesos");
            if (elementos == null) {
                return procesos;
            }
            if (elementos.isEmpty()) {
                error("procesos", "la lista de procesos está vacía");
            }
            Set<String> identificadores = new HashSet<>();
            for (int indice = 0; indice < elementos.size(); indice++) {
                String ruta = "procesos[" + indice + "]";
                Map<?, ?> campos = objeto(elementos.get(indice), ruta);
                if (campos == null) {
                    continue;
                }
                int erroresPrevios = errores.size();
                if (campos.get("id") instanceof String id) {
                    ruta += " (id \"" + id + "\")";
                }
                advertirDesconocidos(campos, CAMPOS_PROCESO, ruta);
                String id = texto(campos, "id", ruta + ".id", true);
                if (id != null) {
                    if (id.isBlank()) {
                        error(ruta + ".id", "no puede estar vacío");
                    } else if (!identificadores.add(id)) {
                        error(ruta, "el id \"" + id + "\" está duplicado");
                    }
                }
                Integer llegada = entero(campos, "tiempoLlegada", ruta + ".tiempoLlegada", true, 0);
                Integer rafaga = entero(campos, "tiempoRafaga", ruta + ".tiempoRafaga", true, 1);
                Integer memoria = entero(campos, "memoria", ruta + ".memoria", true, 1);
                if (memoria != null && memoriaTotal != null && memoria > memoriaTotal) {
                    error(ruta + ".memoria", memoria + " MB excede la memoria total (" + memoriaTotal
                            + " MB); el proceso nunca podría alojarse");
                }
                Integer prioridad = entero(campos, "prioridad", ruta + ".prioridad", false, null);
                List<EventoES> eventos = leerEventos(campos, ruta, rafaga);
                if (errores.size() == erroresPrevios) {
                    procesos.add(new DefinicionProceso(id, llegada, rafaga, memoria, prioridad, eventos, indice));
                }
            }
            return procesos;
        }

        private List<EventoES> leerEventos(Map<?, ?> proceso, String rutaProceso, Integer rafaga) {
            List<EventoES> eventos = new ArrayList<>();
            if (!proceso.containsKey("bloqueosES")) {
                return eventos;
            }
            String rutaLista = rutaProceso + ".bloqueosES";
            List<?> elementos = arreglo(proceso.get("bloqueosES"), rutaLista);
            if (elementos == null) {
                return eventos;
            }
            Integer anterior = null;
            for (int indice = 0; indice < elementos.size(); indice++) {
                String ruta = rutaLista + "[" + indice + "]";
                Map<?, ?> campos = objeto(elementos.get(indice), ruta);
                if (campos == null) {
                    continue;
                }
                int erroresPrevios = errores.size();
                advertirDesconocidos(campos, CAMPOS_EVENTO, ruta);
                Integer enQuantum = entero(campos, "enQuantum", ruta + ".enQuantum", true, null);
                Integer duracion = entero(campos, "duracion", ruta + ".duracion", true, 1);
                if (enQuantum != null) {
                    if (enQuantum < 1 || (rafaga != null && enQuantum >= rafaga)) {
                        error(ruta + ".enQuantum", rafaga == null ? "debe ser mayor o igual a 1"
                                : "debe estar entre 1 y " + (rafaga - 1));
                    }
                    if (anterior != null && enQuantum <= anterior) {
                        error(ruta + ".enQuantum", "debe ser mayor que el anterior (" + anterior + ")");
                    }
                    anterior = enQuantum;
                }
                if (errores.size() == erroresPrevios) {
                    eventos.add(new EventoES(enQuantum, duracion));
                }
            }
            return eventos;
        }

        private Integer entero(Map<?, ?> campos, String campo, String ruta, boolean obligatorio, Integer minimo) {
            if (!campos.containsKey(campo)) {
                if (obligatorio) {
                    error(ruta, "campo obligatorio ausente");
                }
                return null;
            }
            if (!(campos.get(campo) instanceof Long numero)) {
                error(ruta, "debe ser un entero");
                return null;
            }
            if (numero < Integer.MIN_VALUE || numero > Integer.MAX_VALUE) {
                error(ruta, "está fuera del rango de enteros de 32 bits");
                return null;
            }
            if (minimo != null && numero < minimo) {
                error(ruta, "debe ser mayor o igual a " + minimo);
                return null;
            }
            return numero.intValue();
        }

        private String texto(Map<?, ?> campos, String campo, String ruta, boolean obligatorio) {
            if (!campos.containsKey(campo)) {
                if (obligatorio) {
                    error(ruta, "campo obligatorio ausente");
                }
                return null;
            }
            if (!(campos.get(campo) instanceof String valor)) {
                error(ruta, "debe ser un texto");
                return null;
            }
            return valor;
        }

        private Map<?, ?> objeto(Object valor, String ruta) {
            if (valor instanceof Map<?, ?> campos) {
                return campos;
            }
            error(ruta, "debe ser un objeto");
            return null;
        }

        private List<?> arreglo(Object valor, String ruta) {
            if (valor instanceof List<?> elementos) {
                return elementos;
            }
            error(ruta, "debe ser un arreglo");
            return null;
        }

        private void advertirDesconocidos(Map<?, ?> campos, Set<String> conocidos, String ruta) {
            for (Object clave : campos.keySet()) {
                if (!conocidos.contains(clave)) {
                    String ubicacion = ruta.isEmpty() ? clave.toString() : ruta + "." + clave;
                    advertencias.add("Advertencia en config.json: " + ubicacion + ": campo desconocido; se ignora");
                }
            }
        }

        private void error(String ruta, String motivo) {
            errores.add("Error en config.json: " + ruta + ": " + motivo);
        }
    }
}
