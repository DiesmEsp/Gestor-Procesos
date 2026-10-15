package gestor.interfaz.web;

import com.sun.net.httpserver.HttpServer;
import gestor.motor.ControladorSimulacion;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servidor HTTP local con cuatro trabajadores; admite puerto 0 para pruebas aisladas. */
public final class ServidorWeb implements AutoCloseable {
    private final HttpServer servidor;
    private final ExecutorService ejecutor;
    private boolean iniciado;
    private boolean cerrado;

    public ServidorWeb(ControladorSimulacion controlador, int puerto) throws IOException {
        try {
            servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
        } catch (BindException excepcion) {
            throw new IOException("El puerto " + puerto + " ya está en uso", excepcion);
        }
        ejecutor = Executors.newFixedThreadPool(4);
        servidor.setExecutor(ejecutor);
        servidor.createContext("/api/", new ManejadorApi(controlador));
        servidor.createContext("/", new ManejadorEstaticos());
    }

    public synchronized void iniciar() {
        if (cerrado) throw new IllegalStateException("El servidor está cerrado");
        if (!iniciado) {
            servidor.start();
            iniciado = true;
        }
    }

    public int puerto() { return servidor.getAddress().getPort(); }
    public InetSocketAddress direccion() { return servidor.getAddress(); }

    public synchronized void detener() {
        if (!cerrado) {
            cerrado = true;
            servidor.stop(0);
            ejecutor.shutdownNow();
        }
    }

    @Override
    public void close() { detener(); }
}
