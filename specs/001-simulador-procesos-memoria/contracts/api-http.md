# Contrato: API HTTP local

Base: `http://localhost:8080` (puerto configurable con `--puerto`). Solo escucha en la interfaz de
loopback. Todas las respuestas `application/json; charset=utf-8` salvo los estáticos. Errores con
cuerpo `{ "error": "<CODIGO>", "mensaje": "<texto en español>" }`.

## Estáticos

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/` | `index.html` |
| GET | `/estilos.css`, `/app.js` | recurso (`text/css`, `text/javascript`) |
| GET | otra ruta no-API | 404 |

## `GET /api/estado` → 200

```json
{
  "instante": 7,
  "modo": "FINALIZADA",
  "configuracion": {
    "memoriaTotal": 100, "quantum": 2, "maxAdelantamientos": 5, "velocidadMs": 500,
    "politicaMemoria": "BEST_FIT", "planificador": "ROUND_ROBIN"
  },
  "colas": {
    "nuevos":     [ { "id": "P5", "memoria": 60, "adelantamientos": 2, "bloqueante": false } ],
    "listos":     [ "P3", "P1" ],
    "ejecucion":  { "id": "P2", "ticksQuantumUsados": 1 },
    "bloqueados": [ { "id": "P4", "ticksRestantesES": 2 } ],
    "terminados": [ "P6" ]
  },
  "procesos": [
    {
      "id": "P1", "estado": "TERMINADO", "tiempoLlegada": 0, "tiempoRafaga": 4,
      "ticksConsumidos": 4, "ticksRestantes": 0, "memoria": 30, "prioridad": null,
      "bloque": null, "tiempoEspera": 2, "ticksES": 0, "adelantamientos": 0,
      "bloqueante": false, "ticksRestantesES": null,
      "instanteFinalizacion": 6, "tiempoRetorno": 6
    }
  ],
  "memoria": {
    "total": 100, "libre": 100, "mayorHueco": 100, "fragmentacionExterna": 0,
    "bloques": [ { "base": 0, "limite": 99, "tamano": 100, "idProceso": null } ]
  },
  "gantt": [ { "idProceso": "P1", "inicio": 0, "fin": 2 }, { "idProceso": null, "inicio": 2, "fin": 3 } ],
  "bitacora": [
    { "instante": 0, "idProceso": "P1", "origen": "PENDIENTE_LLEGADA", "destino": "NUEVO", "motivo": "Llegada" }
  ],
  "totalEventos": 1,
  "metricas": { "esperaPromedio": 2.5, "retornoPromedio": 6.0, "utilizacionCpu": 100.0, "terminados": 2, "total": 2 },
  "aviso": null
}
```

Notas: los valores del ejemplo solo ilustran la forma de cada campo y no son un estado coherente
de una simulación real. `ejecucion` es `null` si la CPU está ociosa; `bloque` es `{ "base", "limite", "tamano" }` o
`null`; métricas `null` mientras no apliquen; `modo` ∈ `DETENIDA | EN_CURSO | PAUSADA | FINALIZADA`;
`aviso` contiene el último mensaje informativo (p. ej. error al releer el escenario al reiniciar).

### Parámetro `desdeEvento` (bitácora incremental)

`GET /api/estado?desdeEvento=n` (entero ≥ 0, por defecto 0) devuelve en `bitacora` solo los
eventos con índice ≥ n; `totalEventos` indica cuántos eventos hay en total. El dashboard pide
siempre desde el total que ya tiene y acumula la bitácora en el navegador; si `totalEventos` es
menor que lo acumulado (tras un Reiniciar), vacía su copia y pide desde 0. Un valor negativo o
no numérico → 400 `VALOR_INVALIDO`. El resto de campos (incluido el Gantt, compactado en tramos)
se devuelve siempre completo.

Campos por proceso: `bloqueante` es verdadero solo en estado `NUEVO` con `adelantamientos` ≥
`maxAdelantamientos`; `ticksRestantesES` es el número de ticks de E/S pendientes en estado
`BLOQUEADO`, y `null` en otro caso.

## `GET /api/proceso/{id}`

- 200 → el objeto de `procesos[]` anterior más `"instante"` (instante actual). Se devuelve para
  cualquier estado, incluidos `PENDIENTE_LLEGADA` y `TERMINADO`; la CLI decide cómo presentarlo.
- 404 → `{ "error": "PROCESO_NO_EXISTE", "mensaje": "El proceso 'P9' no existe" }`.

## Control

| Método | Ruta | Cuerpo | 200 | Errores |
|---|---|---|---|---|
| POST | `/api/control/iniciar` | — | instantánea | 409 `TRANSICION_INVALIDA` (p. ej. ya finalizada) |
| POST | `/api/control/pausar` | — | instantánea | 409 `TRANSICION_INVALIDA` |
| POST | `/api/control/reiniciar` | — | instantánea (con `aviso` si el archivo no era válido) | — |
| POST | `/api/configuracion` | `{ "quantum"?: int, "memoriaTotal"?: int, "velocidadMs"?: int, "politicaMemoria"?: "BEST_FIT" \| "FIRST_FIT" }` | instantánea | 400 `VALOR_INVALIDO` (incluye política desconocida); 409 `MEMORIA_NO_EDITABLE` (RAM o política con la simulación iniciada); 409 `MEMORIA_INSUFICIENTE` (algún proceso no cabría) |

El planificador no se puede cambiar desde la API: se fija en `config.json` y solo se informa en
`configuracion.planificador`.

La configuración se valida completa antes de aplicar: si un campo es inválido no se aplica ninguno.
Método distinto del indicado → 405 `METODO_NO_PERMITIDO`.
