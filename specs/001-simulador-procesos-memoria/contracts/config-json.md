# Contrato: archivo de escenario `config.json`

Codificación UTF-8. Ruta por defecto `./config.json`; otra con `--config <ruta>` al arrancar.

```json
{
  "configuracion": {
    "memoriaTotal": 1024,
    "quantum": 1,
    "maxAdelantamientos": 5
  },
  "procesos": [
    {
      "id": "P1",
      "tiempoLlegada": 0,
      "tiempoRafaga": 5,
      "memoria": 200,
      "prioridad": 2,
      "bloqueosES": [ { "enQuantum": 2, "duracion": 3 } ]
    },
    { "id": "P2", "tiempoLlegada": 1, "tiempoRafaga": 3, "memoria": 120 }
  ]
}
```

| Ruta | Tipo | Oblig. | Regla |
|---|---|---|---|
| `configuracion` | objeto | no | se aplican defectos a los campos ausentes |
| `configuracion.memoriaTotal` | entero | no | ≥ 1 (MB), defecto 1024 |
| `configuracion.quantum` | entero | no | ≥ 1 (ticks), defecto 1 |
| `configuracion.maxAdelantamientos` | entero | no | ≥ 1, defecto 5 |
| `procesos` | arreglo | sí | al menos 1 elemento |
| `procesos[].id` | texto | sí | no vacío, único |
| `procesos[].tiempoLlegada` | entero | sí | ≥ 0 |
| `procesos[].tiempoRafaga` | entero | sí | ≥ 1 |
| `procesos[].memoria` | entero | sí | 1 … `memoriaTotal` |
| `procesos[].prioridad` | entero | no | sin restricción de rango |
| `procesos[].bloqueosES` | arreglo | no | defecto `[]` |
| `procesos[].bloqueosES[].enQuantum` | entero | sí | 1 … `tiempoRafaga`−1, estrictamente creciente |
| `procesos[].bloqueosES[].duracion` | entero | sí | ≥ 1 |

Campos desconocidos: se ignoran con una advertencia en consola (no son error).

## Errores (se informan todos los encontrados, no solo el primero)

Formato del mensaje: `Error en config.json: <ubicación>: <motivo>`. Ejemplos:

- `Error en config.json: línea 7, columna 12: se esperaba ',' o '}'`
- `Error en config.json: procesos[2] (id "P3"): el id "P3" está duplicado`
- `Error en config.json: procesos[0] (id "P1").memoria: 2048 MB excede la memoria total (1024 MB); el proceso nunca podría alojarse`
- `Error en config.json: procesos[1] (id "P2").bloqueosES[1].enQuantum: debe ser mayor que el anterior (2)`
- `Error en config.json: procesos: la lista de procesos está vacía`

Con errores, el simulador termina con código de salida 1 sin abrir el servidor.
