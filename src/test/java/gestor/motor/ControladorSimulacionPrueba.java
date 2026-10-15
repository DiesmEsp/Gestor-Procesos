package gestor.motor;

import gestor.modelo.*;
import gestor.pruebas.Afirmar;
import gestor.pruebas.Prueba;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static gestor.pruebas.EscenariosMotor.definir;

public class ControladorSimulacionPrueba {
    private static Escenario escenario(int memoria, int quantum, int limite, PoliticaMemoria politica, int demanda) {
        return new Escenario(new ConfiguracionSimulacion(memoria, quantum, limite, politica,
                AlgoritmoPlanificacion.ROUND_ROBIN), List.of(definir("P1", 0, 6, demanda, 0),
                definir("P2", 1, 4, 10, 1, new EventoES(1, 2))));
    }

    private static Escenario inicial() {
        return escenario(100, 2, 5, PoliticaMemoria.BEST_FIT, 30);
    }

    @Prueba
    public void respetaLaTablaDeAccionesYFinaliza() {
        try (var control = new ControladorSimulacion(ControladorSimulacionPrueba::inicial, false)) {
            Afirmar.iguales(ModoSimulacion.DETENIDA, control.instantanea().modo(), "Modo inicial");
            Afirmar.iguales(500, control.instantanea().velocidadMs(), "Velocidad inicial");
            Afirmar.lanza(IllegalStateException.class, control::pausar);
            Afirmar.lanza(IllegalStateException.class, control::avanzarManual);
            control.iniciar();
            Afirmar.lanza(IllegalStateException.class, control::iniciar);
            control.avanzarManual();
            control.pausar();
            Afirmar.iguales(1, control.instantanea().instante(), "Pausar conserva el reloj");
            Afirmar.lanza(IllegalStateException.class, control::pausar);
            Afirmar.lanza(IllegalStateException.class, control::avanzarManual);
            control.iniciar();
            terminar(control);
            Afirmar.iguales(ModoSimulacion.FINALIZADA, control.instantanea().modo(), "Finalización automática");
            Afirmar.lanza(IllegalStateException.class, control::iniciar);
            Afirmar.lanza(IllegalStateException.class, control::pausar);
        }
    }

    @Prueba
    public void permiteRamYPoliticaSoloAntesDeIniciarIncluidaPausaSinTicks() {
        try (var control = new ControladorSimulacion(ControladorSimulacionPrueba::inicial, false)) {
            var error = Afirmar.lanza(IllegalArgumentException.class, () -> control.cambiarMemoriaTotal(29));
            Afirmar.contiene(error.getMessage(), "P1", "La capacidad insuficiente identifica el proceso");
            control.cambiarMemoriaTotal(120);
            control.cambiarPoliticaMemoria(PoliticaMemoria.FIRST_FIT);
            Afirmar.iguales(120, control.instantanea().memoria().total(), "Mapa recreado");
            control.iniciar();
            for (boolean pausar : List.of(false, true)) {
                if (pausar) control.pausar();
                Afirmar.iguales("La capacidad de RAM solo puede modificarse antes de iniciar",
                        Afirmar.lanza(IllegalStateException.class, () -> control.cambiarMemoriaTotal(130)).getMessage(), "RAM protegida");
                Afirmar.iguales("La política de memoria solo puede modificarse antes de iniciar",
                        Afirmar.lanza(IllegalStateException.class, () -> control.cambiarPoliticaMemoria(PoliticaMemoria.BEST_FIT)).getMessage(), "Política protegida");
            }
        }
    }

    @Prueba
    public void recreaElMotorConLaPoliticaElegida() {
        var escenario = new gestor.entrada.LectorEscenario().leer(java.nio.file.Path.of("ejemplos/best-fit.json"));
        try (var control = new ControladorSimulacion(() -> escenario, false)) {
            control.cambiarPoliticaMemoria(PoliticaMemoria.FIRST_FIT);
            control.iniciar();
            for (int tick = 0; tick < 4; tick++) control.avanzarManual();
            var evento = control.instantanea().bitacora().stream()
                    .filter(entrada -> entrada.idProceso().equals("D") && entrada.motivo().startsWith("Memoria asignada"))
                    .findFirst().orElseThrow();
            Afirmar.iguales("Memoria asignada [0–24]", evento.motivo(), "First-Fit toma el primer hueco");
        }
    }

    @Prueba
    public void validaQuantumYVelocidadYAplicaQuantumEnElProximoDespacho() {
        try (var control = new ControladorSimulacion(ControladorSimulacionPrueba::inicial, false)) {
            Afirmar.lanza(IllegalArgumentException.class, () -> control.cambiarQuantum(0));
            for (int valor : List.of(-1, 0, 49, 2001)) {
                Afirmar.lanza(IllegalArgumentException.class, () -> control.cambiarVelocidad(valor));
            }
            control.cambiarVelocidad(2000);
            control.cambiarVelocidad(50);
            control.iniciar();
            control.avanzarManual();
            control.cambiarQuantum(3);
            control.avanzarManual();
            Afirmar.nulo(control.instantanea().colas().ejecucion(), "El turno iniciado con quantum 2 termina a los dos ticks");
            control.avanzarManual();
            control.avanzarManual();
            control.avanzarManual();
            Afirmar.iguales(new TramoGantt("P1", 3, 5), control.instantanea().gantt().getLast(), "El siguiente turno usa el nuevo quantum");
        }
    }

    @Prueba
    public void reiniciarLimpiaElEstadoYTomaLosValoresNoModificadosDelArchivo() {
        var archivo = new AtomicReference<>(inicial());
        try (var control = new ControladorSimulacion(archivo::get, false)) {
            control.iniciar();
            control.avanzarManual();
            archivo.set(escenario(150, 4, 8, PoliticaMemoria.FIRST_FIT, 40));
            control.reiniciar();
            var estado = control.instantanea();
            Afirmar.iguales(ModoSimulacion.DETENIDA, estado.modo(), "Reinicio detenido");
            Afirmar.iguales(0, estado.instante(), "Reloj reiniciado");
            Afirmar.iguales(List.of(), estado.gantt(), "Gantt vacío");
            Afirmar.iguales(List.of(), estado.bitacora(), "Bitácora vacía");
            Afirmar.iguales(archivo.get().configuracion(), estado.configuracion(), "Se relee la configuración completa no modificada");
            Afirmar.iguales(40, estado.procesos().getFirst().memoria(), "Se releen los procesos");
            control.cambiarMemoriaTotal(160);
        }
    }

    @Prueba
    public void reiniciarConservaSoloQuantumRamYPoliticaModificados() {
        var archivo = new AtomicReference<>(inicial());
        try (var control = new ControladorSimulacion(archivo::get, false)) {
            control.cambiarQuantum(3);
            control.cambiarMemoriaTotal(110);
            control.cambiarPoliticaMemoria(PoliticaMemoria.FIRST_FIT);
            control.cambiarVelocidad(70);
            archivo.set(escenario(150, 4, 9, PoliticaMemoria.BEST_FIT, 40));
            control.reiniciar();
            Afirmar.iguales(new ConfiguracionSimulacion(110, 3, 9, PoliticaMemoria.FIRST_FIT,
                    AlgoritmoPlanificacion.ROUND_ROBIN), control.instantanea().configuracion(), "Solo tres valores se superponen");
            Afirmar.iguales(70, control.instantanea().velocidadMs(), "La velocidad no pertenece al archivo");
        }
        try (var control = new ControladorSimulacion(archivo::get, false)) {
            control.cambiarQuantum(7);
            archivo.set(escenario(180, 6, 8, PoliticaMemoria.FIRST_FIT, 50));
            control.reiniciar();
            Afirmar.iguales(archivo.get().configuracion().conQuantum(7), control.instantanea().configuracion(), "Modificar quantum no fija RAM ni política");
        }
    }

    @Prueba
    public void ramConservadaInsuficienteCedeAnteElArchivoConAviso() {
        var archivo = new AtomicReference<>(inicial());
        try (var control = new ControladorSimulacion(archivo::get, false)) {
            control.cambiarMemoriaTotal(50);
            archivo.set(escenario(200, 4, 5, PoliticaMemoria.BEST_FIT, 150));
            control.reiniciar();
            Afirmar.iguales(200, control.instantanea().configuracion().memoriaTotal(), "Se restaura la RAM válida del archivo");
            Afirmar.contiene(control.instantanea().aviso(), "P1", "El aviso identifica el proceso que no cabría");
        }
    }

    @Prueba
    public void falloDelProveedorConservaUltimoArchivoValidoYDejaAviso() {
        var fallo = new AtomicReference<RuntimeException>();
        var archivo = new AtomicReference<>(inicial());
        try (var control = new ControladorSimulacion(() -> {
            if (fallo.get() != null) throw fallo.get();
            return archivo.get();
        }, false)) {
            archivo.set(escenario(160, 5, 7, PoliticaMemoria.FIRST_FIT, 60));
            control.reiniciar();
            for (RuntimeException error : List.of(new IllegalStateException("Lectura fallida"),
                    new gestor.entrada.ErrorConfiguracion(List.of("Error en config.json: prueba")))) {
                fallo.set(error);
                control.iniciar();
                control.avanzarManual();
                control.reiniciar();
                Afirmar.iguales(archivo.get().configuracion(), control.instantanea().configuracion(), "Último archivo válido, sin perder su configuración");
                Afirmar.iguales(0, control.instantanea().instante(), "Reinicio pese al error");
                Afirmar.contiene(control.instantanea().aviso(), error.getMessage(), "El aviso conserva el error del proveedor");
            }
            fallo.set(null);
            control.reiniciar();
            Afirmar.nulo(control.instantanea().aviso(), "Una relectura correcta limpia el aviso");
        }
    }

    @Prueba
    public void sc006ElRelojConPausasYDistintasVelocidadesConservaLaBitacora() throws Exception {
        Instantanea referencia;
        try (var manual = new ControladorSimulacion(ControladorSimulacionPrueba::inicial, false)) {
            manual.iniciar();
            terminar(manual);
            referencia = manual.instantanea();
        }
        try (var real = new ControladorSimulacion(ControladorSimulacionPrueba::inicial)) {
            real.cambiarVelocidad(50);
            real.iniciar();
            for (int umbral : List.of(2, 4)) {
                esperar(() -> real.instantanea().instante() >= umbral);
                real.pausar();
                Instantanea pausa = real.instantanea();
                Thread.sleep(90);
                Afirmar.iguales(pausa, real.instantanea(), "La pausa impide ticks completos y parciales");
                real.cambiarVelocidad(umbral == 2 ? 70 : 50);
                real.iniciar();
            }
            esperar(() -> real.instantanea().modo() == ModoSimulacion.FINALIZADA);
            Afirmar.iguales(referencia.bitacora(), real.instantanea().bitacora(), "SC-006: mismo orden y tiempos simulados");
            Afirmar.iguales(referencia.gantt(), real.instantanea().gantt(), "Mismos turnos");
            Afirmar.iguales(referencia.metricas(), real.instantanea().metricas(), "Mismas métricas");
        }
    }

    private static void terminar(ControladorSimulacion control) {
        for (int tick = 0; tick < 100 && control.instantanea().modo() == ModoSimulacion.EN_CURSO; tick++) control.avanzarManual();
        Afirmar.iguales(ModoSimulacion.FINALIZADA, control.instantanea().modo(), "La ejecución termina dentro de la cota");
    }

    private static void esperar(BooleanSupplier condicion) throws Exception {
        long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(6);
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) Thread.sleep(5);
        Afirmar.verdadero(condicion.getAsBoolean(), "El reloj debe progresar dentro de seis segundos");
    }
}
