# Investigación (Fase 0): Simulador de Gestión de Procesos y Memoria RAM

Todas las incógnitas del Contexto Técnico quedan resueltas aquí. Entorno verificado el 2026-10-15:
JDK `21.0.10` LTS instalado; **no** hay Maven ni Gradle disponibles.

## 1. Construcción sin herramienta de build

- **Decisión**: compilar y empaquetar con `javac`/`jar` del JDK mediante scripts PowerShell
  (`scripts/compilar.ps1`, `scripts/probar.ps1`, `scripts/iniciar.ps1`). Opciones: `javac --release 21
  -encoding UTF-8`.
- **Justificación**: la constitución exige compilar y ejecutar solo con un JDK 21 en Windows; Maven
  y Gradle no están instalados y añadirían descargas.
- **Alternativas**: Maven/Gradle (requieren instalación y red); `Makefile` (no nativo en Windows).

## 2. Framework de pruebas

- **Decisión**: micro-framework propio en `src/test/java/gestor/pruebas/` — anotación `@Prueba`,
  clase `Afirmar` (aserciones) y `EjecutorPruebas` (descubre por reflexión los métodos `@Prueba` de
  las clases `*Prueba` que le pasa `probar.ps1`, reporta en español y devuelve código de salida ≠ 0
  si falla alguna).
- **Justificación**: cumple el Principio I sin excepción (cero descargas), ~150 líneas, y el
  ejecutor es didácticamente transparente. Mantiene la puerta de calidad del Principio VI.
- **Alternativas**: JUnit 5 standalone (permitido por la constitución solo en test, pero exige
  descargar y versionar un jar de ~2,5 MB sin gestor de dependencias). Se podrá migrar después:
  las pruebas usan aserciones con firma similar.

## 3. Lectura y escritura de JSON

- **Decisión**: analizador recursivo descendente propio (`gestor.json.LectorJson`) que produce
  `Map<String,Object>`, `List<Object>`, `String`, `Long`/`Double`, `Boolean`, `null`, con posición
  (línea, columna) en los errores; y `gestor.json.EscritorJson` para serializar instantáneas.
- **Justificación**: el JDK no incluye API JSON; Principio I y V lo exigen propio. El formato de
  entrada es pequeño y conocido.
- **Alternativas**: `javax.script`/Nashorn (eliminado del JDK); expresiones regulares (frágil).

## 4. Servidor HTTP y estáticos

- **Decisión**: `com.sun.net.httpserver.HttpServer` enlazado a `InetAddress.getLoopbackAddress()`,
  puerto 8080 por defecto (argumento `--puerto`). Ejecutor de hilos fijo pequeño (4 hilos).
  Estáticos (`index.html`, `estilos.css`, `app.js`) empaquetados en el jar bajo `/web/` y servidos
  con `getResourceAsStream`. Todas las respuestas llevan `charset=utf-8`.
- **Justificación**: Principio I; solo-localhost según Restricciones Técnicas; un jar autocontenido.
- **Alternativas**: servir desde disco (rompe al ejecutar el jar desde otro directorio).

## 5. Actualización del dashboard

- **Decisión**: sondeo con `fetch` a `GET /api/estado` cada 200 ms (y en cada acción del usuario).
  La instantánea incluye la bitácora completa y el Gantt compactado en tramos.
- **Justificación**: cumple FR-023/SC-004 (< 1 s) sin bibliotecas. Con 50 procesos la instantánea
  es de unas decenas de KB. Al incluir la bitácora completa no se pierden eventos aunque el sondeo
  sea más lento que el reloj (50 ms).
- **Alternativas**: Server-Sent Events (posible con HttpServer, pero añade gestión de conexiones
  largas; se deja como mejora); WebSocket (no soportado por `HttpServer`).

## 6. Concurrencia y determinismo

- **Decisión**: `Motor` es puramente secuencial y sin hilos: `avanzarTick()` aplica el orden de
  eventos de la spec. `ControladorSimulacion` posee un único hilo de reloj (daemon) que llama a
  `avanzarTick()` cada `velocidadMs`; todas las operaciones (tick, control, configuración,
  instantánea) se serializan con un mismo monitor (`synchronized`). Las interfaces solo reciben
  `Instantanea` inmutables (records).
- **Justificación**: Principio II (paso a paso determinista, lecturas consistentes); FR-032 y
  SC-006 (la velocidad o las pausas no alteran el resultado porque solo cambian *cuándo* se llama a
  `avanzarTick()`).
- **Alternativas**: `ScheduledExecutorService` con período fijo (no permite cambiar la velocidad
  sin recrear la tarea; válido pero menos simple).

## 7. Algoritmo de admisión con límite de adelantamientos (FR-015a)

- **Decisión**: en cada instante se construye la lista de candidatos = cola de Espera de Memoria
  (orden de llegada) + recién llegados del instante (orden del archivo). Se recorre con una bandera
  `bloqueada`:
  1. Si `bloqueada`, el candidato queda/entra en espera.
  2. Si cabe (Best-Fit encuentra hueco): se admite → Listo; a cada candidato anterior que siga en
     espera se le suma 1 adelantamiento; si alguno alcanza `maxAdelantamientos`, `bloqueada = true`.
  3. Si no cabe: queda en espera; si su contador ≥ `maxAdelantamientos`, `bloqueada = true`.
- **Justificación**: implementa exactamente la clarificación (opción C, N = 5), es O(n·h) y
  determinista. Un proceso bloqueante entra en cuanto se libera un hueco suficiente.
- **Alternativas**: envejecimiento por tiempo (no fue lo acordado).

## 8. Script PowerShell `gestor.ps1`

- **Decisión**: compatible con Windows PowerShell 5.1 y PowerShell 7; usa `Invoke-WebRequest
  -UseBasicParsing` contra `http://localhost:<Puerto>/api/proceso/<id>` y decodifica el cuerpo como
  UTF-8 explícitamente; guardado en **UTF-8 con BOM** para que PS 5.1 lea bien las tildes. Códigos
  de salida definidos en `contracts/cli-gestor.md`.
- **Justificación**: Windows 11 trae PS 5.1 por defecto; sin BOM PS 5.1 interpreta el script como
  ANSI y corrompe los mensajes en español.
- **Alternativas**: `Invoke-RestMethod` (en PS 5.1 decodifica mal UTF-8 si falta el charset y
  lanza excepción en 404 sin dar acceso cómodo al cuerpo).

## 9. Unidades y representación

- **Decisión**: tiempos en `int` (ticks), memoria en `int` (MB). Direcciones `[base, límite]`
  inclusivas. Métricas en `double`, redondeadas solo al presentar (2 decimales promedios, 1 decimal
  % CPU).
- **Justificación**: coincide con FR-013, FR-029 y FR-030; evita errores de redondeo acumulado.
