# Gestor-Procesos

Simulador educativo para estudiar planificación Round Robin, E/S en paralelo y memoria RAM
con partición variable. Permite seguir cada proceso en un dashboard web y consultarlo desde
PowerShell. Incluye Best-Fit y First-Fit, fusión de huecos y protección anti-inanición mediante
un límite de adelantamientos. Todo se ejecuta con Java 21 y su biblioteca estándar.

> **Especificación completada: T001–T065.** Ver el
> [informe de validación final](specs/001-simulador-procesos-memoria/validacion-final.md)
> para las comprobaciones de la guía rápida y las mediciones de rendimiento.

## Requisitos

- Windows 10/11 con PowerShell 5.1 o 7 y un navegador moderno.
- **JDK 21** en el `PATH`: comprobar con `java -version` y `javac -version`.
- Sin Maven, Gradle, JUnit, bibliotecas JSON, módulos PowerShell ni dependencias web externas.

## Compilar, probar e iniciar

Desde la raíz del repositorio:

```powershell
.\scripts\compilar.ps1
.\scripts\probar.ps1
.\scripts\probar-cli.ps1
.\scripts\iniciar.ps1 -Config .\ejemplos\referencia-rr.json -Puerto 8080
```

La compilación genera `build/gestor-procesos.jar` con los estáticos incluidos. La batería
Java reporta **155 pruebas, 0 fallidas** y verifica automáticamente las capas; la batería
CLI reporta **7 pruebas CLI, 0 fallidas**. Ambas devuelven código 0 al pasar. La segunda
reserva el puerto 18080 y detiene su proceso Java en un bloque `finally`.

Abrir `http://localhost:8080` y pulsar **Iniciar**. En el escenario de referencia, el final
ocurre en el instante 7: Gantt `P1[0,2) P2[2,4) P1[4,6) P2[6,7)`, espera promedio **2,50**,
retorno promedio **6,00** y CPU **100,0 %**. Para cerrar el servidor, usar `Ctrl+C` en su consola.

También se puede ejecutar el jar directamente:

```powershell
java -jar build/gestor-procesos.jar --config config.json --puerto 8080
```

Los valores por defecto son `config.json` y puerto 8080. El puerto 0 solicita un puerto libre;
se informa la URL asignada. El servicio escucha únicamente en la interfaz local de bucle
de retorno (loopback). La CLI evita proxies y esperas de resolución IPv6 usando loopback IPv4.

## Configurar un escenario

`config.json` debe estar en UTF-8. Contiene una configuración opcional y una lista no vacía
de procesos. Ejemplo:

```json
{
  "configuracion": {
    "memoriaTotal": 100,
    "quantum": 2,
    "maxAdelantamientos": 5,
    "politicaMemoria": "BEST_FIT",
    "planificador": "ROUND_ROBIN"
  },
  "procesos": [
    {
      "id": "P1", "tiempoLlegada": 0, "tiempoRafaga": 4, "memoria": 30,
      "bloqueosES": [{ "enQuantum": 1, "duracion": 2 }]
    },
    { "id": "P2", "tiempoLlegada": 1, "tiempoRafaga": 3, "memoria": 20 }
  ]
}
```

Cada proceso requiere `id` único y no vacío, llegada ≥ 0, ráfaga ≥ 1 y memoria entre 1 y
la capacidad total. `prioridad` es un entero opcional; Round Robin no lo utiliza.
`bloqueosES` es opcional: `enQuantum` cuenta **ticks acumulados de CPU**, debe estar entre
1 y ráfaga − 1, y debe aumentar estrictamente; `duracion` debe ser positiva.

Sin configuración se usan RAM 1024 MB, quantum 1, límite de 5 adelantamientos, `BEST_FIT`
y `ROUND_ROBIN`. La alternativa de memoria es `FIRST_FIT`; los códigos distinguen mayúsculas.
Los campos desconocidos generan advertencias. Los errores se acumulan con ubicación y causa;
el arranque termina con código 1 sin abrir un servidor.

Ver el [contrato completo de configuración](specs/001-simulador-procesos-memoria/contracts/config-json.md).

| Archivo en `ejemplos/` | Propósito |
|---|---|
| `referencia-rr.json` | Turnos y métricas de referencia sin E/S |
| `referencia-es.json` | Bloqueo y ocio de CPU; utilización final 60,0 % |
| `best-fit.json`, `first-fit.json` | Comparar asignaciones `[70–94]` y `[0–24]` |
| `inanicion.json` | Protección tras cinco adelantamientos |
| `carga-50.json` | Cincuenta procesos con E/S para comprobar rendimiento |
| `config.json` | Escenario didáctico de seis procesos; también está en la raíz |

## Dashboard y controles

El dashboard muestra el instante, modo, cinco colas, CPU y consumo del turno, RAM proporcional
con huecos rayados, Gantt acumulativo, métricas y bitácora con los eventos recientes arriba.
Los colores se conservan por identificador de proceso. En espera de RAM se indican memoria
requerida, adelantamientos, mayor hueco y causa de espera; se destaca el proceso bloqueante.

- **Iniciar/Reanudar** y **Pausar** controlan el reloj entre ticks completos.
- El intervalo puede variar entre 50 y 2000 ms por tick; el inicial es 500 ms.
- **Aplicar** cambia los campos editados. El quantum nuevo se usa en el siguiente despacho.
- RAM y política se pueden editar únicamente en modo **DETENIDA**, incluso si la pausa
  ocurrió en el instante 0.
- **Reiniciar** relee el mismo archivo, vuelve a 0 y vacía Gantt, bitácora y métricas.
  Solo conserva quantum, RAM y política modificados explícitamente desde la interfaz.
  Si esa RAM no basta para un proceso nuevo, utiliza la del archivo con un aviso. Si la
  relectura falla, usa el último escenario válido e informa el error.

El navegador consulta cada 200 ms y acumula la bitácora incremental. A velocidades altas
puede omitir estados visuales intermedios; todos los eventos permanecen disponibles. Pausas
y velocidad no alteran la secuencia lógica ni el resultado final.

## Consultar desde PowerShell

Con el servidor activo, abrir otra consola:

```powershell
.\gestor.ps1 P2
.\gestor.ps1 -IdProceso P1 -Puerto 8080
$LASTEXITCODE
```

La ficha informa estado, ticks de CPU consumidos/restantes, bloque de RAM, tiempo de espera,
adelantamientos y, cuando corresponde, E/S restante y prioridad de admisión. Si aún no llegó,
se informa su instante de llegada. Los scripts están guardados en UTF-8 con BOM para PS 5.1.

| Código | Situación |
|---|---|
| 0 | Consulta correcta o proceso pendiente de llegada |
| 1 | Falta el identificador; muestra el uso |
| 2 | El proceso no existe |
| 3 | El proceso ya terminó; informa el instante final |
| 4 | El servidor no está disponible |
| 5 | Respuesta HTTP o contenido inesperado |

Los errores van a `stderr`. La consulta tiene un plazo de 2 s; la batería mide las
invocaciones completas de los casos activos de SC-005. Ver el
[formato exacto de la ficha](specs/001-simulador-procesos-memoria/contracts/cli-gestor.md).

## Modelo de tiempo y orden de eventos

Un **tick** es el intervalo `[t, t+1)`. El quantum limita la CPU consecutiva de un turno.
Asignar RAM y cambiar de contexto no consumen ticks. En cada instante se respeta este orden:

1. Intentar admitir a quienes ya esperaban RAM.
2. Incorporar las llegadas de `t` por orden de archivo y continuar la admisión. Cada admisión
   posterior suma un adelantamiento a los anteriores aún en espera; al alcanzar el límite,
   ningún posterior entra hasta admitir al protegido. Los anteriores sí pueden entrar.
3. Devolver a Listos los procesos cuya E/S terminó, en orden de bloqueo.
4. Reencolar al expropiado al final de Listos, tras las llegadas y los retornos de E/S.
5. Despachar si la CPU está libre y fijar el quantum de ese turno.
6. Consumir un tick de CPU y sumar las esperas y la E/S de ese intervalo.
7. Evaluar a `t+1`: primero finalización y liberación de RAM; luego solicitud de E/S;
   por último agotamiento del quantum. Avanzar el reloj a `t+1`.

La memoria liberada queda disponible para el paso 1 de ese nuevo instante. Durante E/S
el proceso conserva su partición; varios procesos realizan E/S en paralelo. No hay
compactación ni intercambio. Los huecos adyacentes se fusionan al liberar memoria.

## Métricas

Todas las duraciones lógicas están en ticks:

- **Retorno** = instante de finalización − llegada; nulo hasta terminar.
- **Espera** = ticks en Listo + ticks en espera de RAM.
- Al terminar: **retorno = ráfaga + ticks de E/S + espera**.
- **Utilización de CPU** = ticks con CPU ocupada / instante actual × 100.
  Incluye los intervalos ociosos; es nula en el instante 0.
- **Promedios** = suma de tiempos / cantidad de procesos terminados; nulos si no hay ninguno.
- **Fragmentación externa** = RAM libre total − tamaño del mayor hueco contiguo.

La interfaz usa coma decimal: dos decimales en promedios y uno en utilización. La API
mantiene los valores numéricos sin redondear. Ver el [modelo de datos](specs/001-simulador-procesos-memoria/data-model.md).

## Capas y verificación

| Paquete | Responsabilidad | Dependencias del proyecto permitidas |
|---|---|---|
| `gestor.modelo` | Definiciones y PCB | Su propio paquete |
| `gestor.recursos` | Estrategias de CPU y RAM | Modelo y recursos |
| `gestor.motor` | Ticks, reloj, métricas e instantáneas | Recursos, modelo y motor |
| `gestor.json` | Lector y escritor JSON propios | Su propio paquete |
| `gestor.entrada` | Carga y validación | Modelo, JSON y entrada |
| `gestor.interfaz.web` | HTTP y recursos estáticos | Motor, modelo, JSON e interfaz |
| `gestor.Principal` | Composición y arranque | Es el único que importa entrada |

`Motor` es secuencial, sin hilos ni E/S. El controlador posee el reloj y sincroniza ticks,
acciones y capturas sobre un mismo monitor. La API entrega registros inmutables y utiliza
`HttpServer` del JDK con cuatro trabajadores. Ver el
[contrato HTTP](specs/001-simulador-procesos-memoria/contracts/api-http.md).

```powershell
.\scripts\verificar-capas.ps1
javadoc -quiet -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -Xdoclint:all -Werror -d build/javadoc -sourcepath src/main/java -subpackages gestor
```

El verificador se ejecuta al final de `probar.ps1`, rechaza dependencias prohibidas y devuelve
código 1 al detectar una infracción. `build/` está excluido de Git. El microejecutor propio
descubre métodos `@Prueba` y muestra `[OK]` / `[FALLO]` sin dependencias de pruebas externas.
`RendimientoPrueba` verifica 2000 llamadas a tick + captura + serialización (< 10 ms de media),
sondeo de los últimos cuatro ticks (< 500 KB) y serialización final completa (< 200 ms).

Para comprobar SC-004 en el navegador, iniciar `ejemplos/carga-50.json` a 50 ms por tick.
Las herramientas de desarrollo permiten inspeccionar `#conexion`: los atributos
`data-respuestas-estado`, `data-latencia-estado-ms` y `data-maxima-latencia-estado-ms` registran
las respuestas de estado y su latencia, incluida descarga y análisis del JSON. No afectan
la presentación. Comparar también el instante visible con `/api/estado` durante la ejecución;
el sondeo debe mantener un retraso inferior a un segundo.

La [guía rápida](specs/001-simulador-procesos-memoria/quickstart.md) contiene la validación
reproducible completa. La trazabilidad está en [tasks.md](specs/001-simulador-procesos-memoria/tasks.md).
