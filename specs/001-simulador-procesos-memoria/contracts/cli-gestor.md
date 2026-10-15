# Contrato: CLI `gestor.ps1`

```text
.\gestor.ps1 [-IdProceso] <id> [-Puerto <int> = 8080]
```

Compatible con Windows PowerShell 5.1 y PowerShell 7. Archivo guardado en UTF-8 con BOM. Consulta
`GET http://localhost:<Puerto>/api/proceso/<id>` (ver `api-http.md`).

## Salida normal (código 0)

```text
Proceso P2  (instante actual: 5)
  Estado ..................: En Ejecución
  Quantums consumidos .....: 2
  Quantums restantes ......: 1
  Bloque de RAM ...........: [30 – 49] (20 MB)
  Tiempo de espera ........: 3 ticks
  Adelantamientos .........: 0
```

- `Bloque de RAM` muestra `sin asignar (esperando memoria)` en estado Nuevo.
- En estado Nuevo con adelantamientos ≥ límite se añade la línea `  Prioridad de admisión : sí (bloqueante)`.
- En Bloqueado se añade `  E/S restante ............: <n> ticks`.
- Estados mostrados: `Nuevo / Espera de memoria`, `Listo`, `En Ejecución`, `Bloqueado`,
  `Terminado`, `Pendiente de llegada`.

## Casos especiales y códigos de salida

| Situación | Mensaje (stderr para errores) | Código |
|---|---|---|
| Consulta correcta | ficha anterior | 0 |
| Proceso pendiente de llegada | `El proceso 'P7' todavía no ha llegado al sistema (llega en el instante 12).` | 0 |
| Falta el argumento | `Uso: .\gestor.ps1 <idproceso> [-Puerto 8080]` | 1 |
| Proceso inexistente (404) | `Error: el proceso 'P9' no existe en la simulación.` | 2 |
| Proceso terminado | `Error: el proceso 'P1' ya finalizó su ciclo de vida (terminó en el instante 6).` | 3 |
| Simulador no disponible | `Error: no se pudo conectar con el simulador en http://localhost:8080. ¿Está en ejecución?` | 4 |
| Respuesta inesperada | `Error: respuesta inesperada del simulador (<código HTTP>).` | 5 |
