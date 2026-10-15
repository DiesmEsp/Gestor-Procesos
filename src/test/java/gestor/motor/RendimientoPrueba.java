package gestor.motor;

import gestor.entrada.LectorEscenario;
import gestor.interfaz.web.ConvertidorInstantanea;
import gestor.json.EscritorJson;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Locale;

/** Cotas de SC-004 con datos reales y el mismo adaptador JSON que utiliza la API. */
public class RendimientoPrueba {
    @Prueba
    public void dosMilTicksConCapturaYSerializacionPromedianMenosDeDiezMilisegundos() {
        Motor calentamiento = crearMotor();
        for (int tick = 0; tick < 100; tick++) {
            calentamiento.avanzarTick();
            serializar(calentamiento, 0);
        }
        Motor motor = crearMotor();
        long caracteres = 0;
        long inicio = System.nanoTime();
        for (int tick = 0; tick < 2000; tick++) {
            motor.avanzarTick();
            // Se serializa el historial completo incluso después de finalizar, sin omitir llamadas.
            caracteres += serializar(motor, 0).length();
        }
        double promedioMs = (System.nanoTime() - inicio) / 1_000_000.0 / 2000;
        Afirmar.verdadero(motor.finalizada(), "Los cincuenta procesos deben terminar");
        Afirmar.iguales(50, motor.metricas().terminados(), "La carga no se reduce para medirla");
        Afirmar.verdadero(caracteres > 2000, "Se consumen las dos mil respuestas JSON");
        System.out.printf(Locale.ROOT, "[INFO] SC-004: 2000 ticks, %.3f ms por tick%n", promedioMs);
        Afirmar.verdadero(promedioMs < 10, "SC-004: avanzar, capturar y serializar debe promediar menos de 10 ms; real: " + promedioMs);
    }

    @Prueba
    public void sondeoDeLosUltimosCuatroTicksOcupaMenosDeQuinientosKilobytes() {
        CapturaFinal captura = finalizarCarga();
        String respuesta = EscritorJson.escribir(ConvertidorInstantanea.aMapa(captura.estado(), captura.desdeEvento()));
        int bytes = respuesta.getBytes(StandardCharsets.UTF_8).length;
        Afirmar.verdadero(captura.desdeEvento() > 0, "El sondeo usa un índice real de eventos anteriores");
        Afirmar.verdadero(captura.desdeEvento() < captura.estado().bitacora().size(), "Los últimos cuatro ticks incluyen eventos");
        System.out.println("[INFO] SC-004: sondeo incremental de cuatro ticks, " + bytes + " bytes UTF-8");
        Afirmar.verdadero(bytes < 500_000, "SC-004: la respuesta incremental completa debe ocupar menos de 500 KB");
    }

    @Prueba
    public void respuestaFinalCompletaSeSerializaEnMenosDeDoscientosMilisegundos() {
        Instantanea estado = finalizarCarga().estado();
        long inicio = System.nanoTime();
        String respuesta = EscritorJson.escribir(ConvertidorInstantanea.aMapa(estado, 0));
        double milisegundos = (System.nanoTime() - inicio) / 1_000_000.0;
        Afirmar.contiene(respuesta, "\"totalEventos\":" + estado.bitacora().size(), "La medición incluye toda la bitácora");
        System.out.printf(Locale.ROOT, "[INFO] SC-004: respuesta final completa, %.3f ms%n", milisegundos);
        Afirmar.verdadero(milisegundos < 200, "SC-004: la respuesta final debe serializarse en menos de 200 ms; real: " + milisegundos);
    }

    private static Motor crearMotor() {
        var escenario = new LectorEscenario().leer(Path.of("ejemplos/carga-50.json"));
        return new Motor(escenario, FabricaRecursos.crearPolitica(escenario.configuracion().politicaMemoria()),
                FabricaRecursos.crearPlanificador(escenario.configuracion().planificador()));
    }

    private static String serializar(Motor motor, int desdeEvento) {
        return EscritorJson.escribir(ConvertidorInstantanea.aMapa(motor.instantanea()
                .con(motor.finalizada() ? ModoSimulacion.FINALIZADA : ModoSimulacion.EN_CURSO, 50, null), desdeEvento));
    }

    private static CapturaFinal finalizarCarga() {
        Motor motor = crearMotor();
        var indices = new ArrayDeque<Integer>();
        indices.add(0);
        for (int tick = 0; tick < 2000 && !motor.finalizada(); tick++) {
            motor.avanzarTick();
            indices.addLast(motor.bitacora().size());
            if (indices.size() > 5) indices.removeFirst();
        }
        Afirmar.verdadero(motor.finalizada(), "La carga debe concluir dentro de dos mil ticks");
        return new CapturaFinal(motor.instantanea().con(ModoSimulacion.FINALIZADA, 50, null), indices.getFirst());
    }

    private record CapturaFinal(Instantanea estado, int desdeEvento) {}
}
