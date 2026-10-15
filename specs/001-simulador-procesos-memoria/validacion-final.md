# Validación final de la especificación

Fecha: 2026-10-15. Tareas T001–T065 completadas.

Entorno: Windows, OpenJDK Temurin 21.0.10+7, PowerShell 7.6.5 y Windows PowerShell
5.1.26100.9711. Sin dependencias externas de producción o de pruebas Java.

## Comprobaciones automatizadas

| Comprobación | Resultado |
|---|---|
| `scripts/compilar.ps1` | Código 0; jar con HTML, CSS y JavaScript |
| `scripts/probar.ps1`, PS 7 y PS 5.1 | 155 pruebas, 0 fallidas; código 0 |
| `scripts/probar-cli.ps1`, PS 7 y PS 5.1 | 7 pruebas CLI, 0 fallidas; código 0 |
| Verificador integrado de capas | 35 fuentes Java, sin infracciones |
| Verificador con entradas aisladas | 20 casos válidos y prohibidos con el código esperado |
| Javadoc con `-Xdoclint:all -Werror` | Código 0; sin errores ni advertencias |
| Respuesta HTTP 503 a la CLI | Salida 5 en ambas versiones de PowerShell |

Los 20 casos del verificador incluyen las dependencias de entrada, JSON, interfaz, motor,
HTTP, red, E/S y archivos; dependencias externas; importaciones estáticas multilínea;
la excepción de `Principal`; e importaciones aparentes dentro de comentarios y cadenas.
También se comprueba la dirección completa de dependencias indicada en `plan.md`.

La auditoría cubrió las clases, constructores, constantes, métodos de interfaces y métodos
públicos explícitos. Los componentes de los registros se documentan mediante `@param`,
incluidos los valores nulos y sus unidades. Se conservaron los nombres exigidos por el JDK
(`main`, `handle`, `close`, `serialVersionUID`) y los códigos del contrato (`BEST_FIT`,
`FIRST_FIT`, `ROUND_ROBIN`). Se comparó el código Java antes y después de retirar los
comentarios: solo se hicieron explícitos siete constructores vacíos equivalentes a los
implícitos; la lógica de simulación permanece intacta.

## Rendimiento del motor: SC-004

`RendimientoPrueba` utiliza `ejemplos/carga-50.json` y el adaptador JSON real de la API.

| Medición | Cota | PS 7 | PS 5.1 |
|---|---|---|---|
| Tick + instantánea + conversión + JSON, media de 2000 llamadas | < 10 ms | 1,205 ms | 1,217 ms |
| Respuesta incremental de los últimos cuatro ticks, UTF-8 | < 500.000 bytes | 28.623 bytes | 28.623 bytes |
| Conversión y serialización de la respuesta final completa | < 200 ms | 4,268 ms | 4,129 ms |

El calentamiento queda fuera de la medición. Se realizan las 2000 llamadas completas,
incluida la serialización de toda la bitácora; el escenario termina en el instante 500 y
las llamadas posteriores conservan el estado final, conforme al contrato de `Motor`.
La comprobación incremental incluye todos los demás campos de la respuesta, no solo eventos.

## Guía rápida, apartados 1–5

| Apartado | Evidencia |
|---|---|
| §1 Compilar y probar | Jar generado, 155 pruebas Java, 7 pruebas CLI y verificación de capas correctos |
| §2 Round Robin de referencia | Instante final 7; `P1[0,2) P2[2,4) P1[4,6) P2[6,7)`; espera 2,50; retorno 6,00; CPU 100,0 % |
| §2 E/S de referencia | CPU 60,0 %; ocio `[1,3)` visible en Gantt |
| §3 Pausa y reanudación | Estado conservado entre ticks; RAM y política deshabilitadas incluso en pausa |
| §3 Quantum nuevo | Cambio a 3 durante el primer turno: este conserva `[0,2)`; el siguiente turno usa `[2,5)` |
| §3 Política | Selector First-Fit asigna a D `[0–24]`; Best-Fit asigna `[70–94]` |
| §3 Reiniciar | Instante 0, bitácora vacía y controles editables; relectura y conservación selectiva cubiertas por las pruebas del controlador |
| §4 CLI | Pendiente, inexistente, ficha activa, terminado y desconexión según los códigos del contrato |
| §5 Configuración inválida | `iniciar.ps1` con `id-duplicado.json` informa proceso y causa, devuelve 1 sin arrancar otro servidor |

Las cuatro consultas medidas de SC-005 tardaron **0,377–0,439 s** en PS 7 y
**0,903–1,020 s** en PS 5.1, incluyendo el arranque del proceso PowerShell de cada consulta.
Se verificó el arranque mediante `scripts/iniciar.ps1` en el puerto 8080 y la navegación a
`http://localhost:8080`. Los procesos y puertos de prueba se liberaron al finalizar.

## Dashboard bajo carga: SC-004

Se ejecutó el jar final mediante `iniciar.ps1 -Config ejemplos/carga-50.json -Puerto 8080`,
con intervalo de 50 ms por tick. El navegador utilizó el sondeo real de la aplicación.

| Medición | Resultado |
|---|---|
| Procesos finalizados / instante | 50 de 50 / 500 |
| Bitácora en la API / elementos de eventos en el navegador | 822 / 822 |
| Respuestas del navegador en la ventana de ejecución medida | 156; máximo 34,4 ms |
| Respuestas registradas hasta cerrar la página | 1000; máximo 41,5 ms |
| Muestras independientes de la API, aproximadamente cada 100 ms | 455; sin errores |
| Muestras visuales correlacionadas durante toda la ejecución | 140 |
| Diferencia máxima de instante | 3 ticks |
| Antigüedad máxima observada del estado visible | 323 ms, inferior a 1 s |

Las latencias se registran con `performance.now()` e incluyen descarga y análisis del JSON;
pueden inspeccionarse en los atributos `data-*` de `#conexion`. Las muestras visuales leen
el instante del DOM, y se correlacionan por tiempo de reloj con las respuestas independientes
de la API. La antigüedad se calcula desde la última muestra de API cuyo instante no supera
al visible, por lo que incluye conservadoramente la resolución del muestreo. La medición
cubre desde el inicio hasta `FINALIZADA`. Los archivos de muestras y la captura quedan en
`build/`, excluido de Git. Estas cifras corresponden al entorno indicado y se pueden
reproducir con la guía rápida y los contadores del navegador.

No quedan tareas pendientes ni excepciones a la arquitectura que justificar.
