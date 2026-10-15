# Constitución de Gestor-Procesos

Gestor-Procesos es un simulador educativo de gestión y orquestación de procesos y memoria
para la asignatura de Sistemas Operativos.

## Core Principles

### I. Stack Tecnológico Estándar y Cero Dependencias Pesadas

- El lenguaje del proyecto DEBE ser Java 21 (LTS), con un enfoque tradicional y estructurado
  (clases, interfaces y paquetes claros; sin magia de reflexión ni generación de código).
- El código de producción DEBE usar exclusivamente la biblioteca estándar del JDK
  (`java.base`, `java.net`, `java.util`, `java.nio`, `jdk.httpserver`, etc.).
- Están PROHIBIDOS los frameworks y bibliotecas de terceros en tiempo de ejecución
  (Spring, Quarkus, Micronaut, Jackson, Gson, Lombok y similares).
- La capa web DEBE implementarse con el servidor HTTP nativo del JDK
  (`com.sun.net.httpserver.HttpServer`).
- El frontend DEBE consistir en recursos estáticos livianos: HTML5, CSS y JavaScript Vanilla,
  sin dependencias externas, CDN, bundlers ni frameworks (React, Vue, jQuery, etc.).
- Única excepción permitida: bibliotecas de pruebas (p. ej. JUnit 5) con alcance exclusivo de
  test; NUNCA deben formar parte del artefacto ejecutable.

**Justificación**: el objetivo es didáctico; cada mecanismo debe ser visible y comprensible
en el propio código, y el simulador debe ejecutarse con solo un JDK 21 instalado.

### II. Arquitectura Desacoplada en Capas

- El sistema DEBE separarse estrictamente en cuatro capas con dependencias en un solo sentido:
  1. **Motor de Simulación (Core Engine)**: reloj, planificación y transiciones de estado.
  2. **Capa de Recursos**: modelo de CPU y de memoria RAM.
  3. **Entrada de Datos**: lector y validador de la configuración JSON.
  4. **Interfaces de Usuario**: Web (HTTP + estáticos) y CLI (PowerShell).
- El Motor y los Recursos NO DEBEN depender de clases de las capas de Interfaz ni de E/S
  (ni HTTP, ni consola, ni sistema de archivos); se comunican mediante interfaces Java y
  objetos de transferencia (instantáneas inmutables del estado).
- El Motor DEBE avanzar mediante un reloj de tiempo discreto (ticks); un tick es la unidad
  atómica de simulación y el quantum se expresa en ticks.
- El Motor DEBE poder ejecutarse paso a paso (un tick a la vez) de forma determinista, sin
  hilos, para permitir pruebas reproducibles.
- La ejecución en tiempo real DEBE soportar velocidad ajustable (intervalo entre ticks),
  pausa y reanudación, sin alterar el resultado lógico de la simulación.
- El acceso concurrente al estado (hilo del reloj vs. peticiones HTTP) DEBE estar sincronizado;
  las interfaces solo leen instantáneas consistentes.

**Justificación**: el desacoplamiento permite probar la lógica de SO de forma aislada y
cambiar o añadir interfaces sin tocar el núcleo.

### III. Gestión Rigurosa de Recursos (CPU y RAM)

- **Planificación de CPU**: Round Robin con quantum configurable es OBLIGATORIO.
- Los planificadores DEBEN implementarse detrás de una interfaz común (patrón Estrategia), de
  modo que FCFS, Prioridades y SJF puedan añadirse sin modificar el Motor.
- **Ciclo de vida**: cada proceso DEBE tener un PCB y recorrer los estados Nuevo → Listo →
  Ejecución → (Bloqueado → Listo)* → Terminado. Toda transición DEBE ser explícita, válida
  según este diagrama y registrada con el tick en que ocurre.
- **E/S simulada**: las ráfagas de E/S definidas para un proceso DEBEN enviarlo a Bloqueado
  durante su duración y devolverlo a Listo al completarse.
- **Memoria RAM**: DEBE administrarse con particionamiento dinámico (variable), manteniendo
  la lista de bloques asignados y de huecos libres, con políticas First-Fit y Best-Fit
  seleccionables. Los huecos adyacentes DEBEN fusionarse al liberar memoria.
- Un proceso SOLO puede pasar de Nuevo a Listo si la RAM puede alojarlo; en caso contrario
  permanece en Nuevo. Un proceso cuyo requerimiento supere la RAM total DEBE rechazarse con un
  mensaje claro.
- La memoria de un proceso DEBE liberarse al pasar a Terminado.

**Justificación**: el simulador debe reflejar fielmente los conceptos teóricos de planificación
y administración de memoria evaluados en el curso.

### IV. Interfaces Duales (Web Interactiva + CLI PowerShell)

- **Interfaz Web**: DEBE ofrecer un dashboard que muestre en tiempo real las colas Nuevos,
  Listos, Ejecución, Bloqueados y Terminados; un mapa visual de la RAM (bloques y huecos);
  métricas acumuladas; y controles de iniciar, pausar, reanudar y velocidad.
- La Web DEBE obtener el estado del Motor mediante endpoints HTTP que devuelvan JSON
  (sondeo periódico u otra técnica estándar del navegador, sin bibliotecas externas).
- **Interfaz CLI**: DEBE existir un script PowerShell (`.\gestor.ps1 <idproceso>` o equivalente)
  que consulte al motor en ejecución y muestre el estado detallado del proceso indicado
  (PID, estado, tiempos, memoria asignada, ráfagas restantes).
- La CLI DEBE comunicarse con el motor en ejecución (p. ej. vía la API HTTP local); NO DEBE
  leer archivos intermedios ni re-ejecutar la simulación.
- La CLI DEBE informar con un mensaje en español y código de salida distinto de cero cuando el
  motor no esté disponible o el PID no exista.

**Justificación**: la Web facilita la comprensión visual y la CLI permite la inspección precisa
y automatizable del estado de un proceso.

### V. Entrada de Datos y Formato

- La carga inicial de procesos DEBE realizarse desde un archivo `.json`.
- Cada proceso DEBE especificar: PID, tiempo de llegada, tiempo de ráfaga (CPU), memoria
  requerida y sus ráfagas de E/S (momento de inicio y duración); campos opcionales como la
  prioridad pueden añadirse para planificadores futuros.
- Los parámetros de simulación (quantum, tamaño de RAM, política de memoria, algoritmo de
  planificación) DEBEN ser configurables desde el JSON o desde argumentos de arranque.
- El análisis del JSON DEBE implementarse con un lector propio basado en la biblioteca estándar
  (Principio I).
- Toda entrada inválida (PID duplicado, valores negativos, campos faltantes, JSON mal formado)
  DEBE rechazarse antes de iniciar la simulación, indicando en español el campo y el motivo.

**Justificación**: un formato declarativo y validado hace los escenarios reproducibles y
fáciles de compartir entre estudiantes y docentes.

### VI. Calidad, Pruebas y Trazabilidad

- El desarrollo DEBE guiarse por pruebas unitarias automatizadas: la prueba se escribe antes
  o junto con la lógica que valida, y debe fallar antes de implementarla.
- Es OBLIGATORIO cubrir con pruebas: la lógica de quantums y expropiación, las transiciones del
  PCB, el manejo de E/S, el cálculo de métricas (tiempo de espera, tiempo de retorno, % de uso
  de CPU) y la asignación, liberación, fusión de huecos y fragmentación de memoria.
- Las pruebas del Motor DEBEN usar escenarios deterministas con resultados esperados calculados
  a mano (p. ej. diagramas de Gantt de referencia).
- Toda la nomenclatura (clases, métodos, variables), comentarios, documentación técnica y
  mensajes del sistema DEBEN redactarse 100% en español. Se exceptúan las palabras reservadas
  del lenguaje y los nombres de APIs del JDK.
- El Motor DEBE mantener una bitácora de eventos (tick, PID, transición, motivo) consultable
  desde las interfaces, para trazabilidad didáctica.

**Justificación**: un simulador educativo con resultados incorrectos enseña conceptos
erróneos; las pruebas y la bitácora garantizan su exactitud y explicabilidad.

## Restricciones Técnicas Adicionales

- El proyecto DEBE compilar y ejecutarse con únicamente un JDK 21 instalado en Windows;
  la herramienta de construcción elegida no DEBE introducir dependencias de ejecución.
- El servidor web DEBE escuchar por defecto solo en `localhost` con un puerto configurable.
- Las fórmulas de métricas DEBEN documentarse en el código y en la documentación técnica:
  - Tiempo de retorno = tiempo de finalización − tiempo de llegada.
  - Tiempo de espera = tiempo de retorno − ráfaga de CPU − tiempo total en E/S.
  - % de uso de CPU = ticks con CPU ocupada / ticks totales × 100.
- El estado expuesto por la API DEBE serializarse a JSON con un escritor propio
  (Principio I).

## Flujo de Desarrollo y Puertas de Calidad

- Cada funcionalidad DEBE seguir el flujo Spec Kit: especificación → plan → tareas →
  implementación, y el plan DEBE incluir una verificación explícita de los seis principios.
- Ningún cambio se considera terminado si las pruebas unitarias no pasan en su totalidad.
- Toda nueva dependencia, excepción a la separación de capas o desviación de esta constitución
  DEBE justificarse por escrito en el plan (sección de complejidad) y aprobarse antes de
  implementarse.
- Las revisiones de código DEBEN verificar: ausencia de dependencias prohibidas, dirección de
  dependencias entre capas, cobertura de pruebas de la lógica crítica e idioma español.

## Governance

- Esta constitución prevalece sobre cualquier otra práctica o guía del proyecto.
- Las enmiendas DEBEN documentarse en este archivo con su Informe de Impacto, incrementar la
  versión y actualizar la fecha de última enmienda.
- Versionado semántico de la constitución:
  - MAJOR: eliminación o redefinición incompatible de principios o reglas de gobierno.
  - MINOR: adición de un principio o sección, o ampliación material de una guía.
  - PATCH: aclaraciones, redacción o correcciones sin cambio semántico.
- El cumplimiento DEBE verificarse en cada plan (`/speckit-plan`, "Constitution Check") y en
  cada revisión de código; los incumplimientos bloquean la integración.

**Version**: 1.0.0 | **Ratified**: 2026-10-15 | **Last Amended**: 2026-10-15
