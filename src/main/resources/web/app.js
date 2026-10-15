"use strict";

(() => {
  const elemento = (id) => document.getElementById(id);
  const estados = { PENDIENTE_LLEGADA: "Pendiente de llegada", NUEVO: "Nuevo / Espera de memoria", LISTO: "Listo", EJECUCION: "En ejecución", BLOQUEADO: "Bloqueado", TERMINADO: "Terminado" };
  const politicas = { BEST_FIT: "Best-Fit", FIRST_FIT: "First-Fit" };
  const campos = { quantum: elemento("quantum"), memoriaTotal: elemento("memoria-total"), politicaMemoria: elemento("politica"), velocidadMs: elemento("velocidad") };
  const cambios = new Set();
  const decimales = new Intl.NumberFormat("es-ES", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const porcentaje = new Intl.NumberFormat("es-ES", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  let ultimaInstantanea = null;
  let eventosRecibidos = 0;
  let eventos = [];
  let conectado = false;
  let accionPendiente = false;
  let sondeoPendiente = false;
  let secuencia = Promise.resolve();
  let respuestasEstado = 0;
  let maximaLatenciaEstadoMs = 0;

  function crear(etiqueta, clase, texto) {
    const nodo = document.createElement(etiqueta);
    if (clase) nodo.className = clase;
    if (texto !== undefined) nodo.textContent = texto;
    return nodo;
  }

  function colorear(nodo, id) {
    let codigo = 0;
    for (const caracter of id) codigo = (Math.imul(codigo, 31) + caracter.codePointAt(0)) | 0;
    const tono = (Math.abs(codigo) * 137.508) % 360;
    nodo.style.setProperty("--color-proceso", `hsl(${tono}, 55%, 29%)`);
    nodo.style.setProperty("--fondo-proceso", `hsl(${tono}, 48%, 91%)`);
  }

  function tarjeta(id) {
    const nodo = crear("li", "tarjeta-proceso");
    colorear(nodo, id);
    nodo.append(crear("span", "id-proceso", id));
    return nodo;
  }

  function mostrarMensaje(texto, error = false) {
    const mensaje = elemento("mensaje");
    mensaje.textContent = texto;
    mensaje.classList.toggle("error", error);
    mensaje.hidden = !texto;
  }

  function actualizarControles() {
    const modo = ultimaInstantanea?.modo;
    const disponible = conectado && !accionPendiente;
    elemento("iniciar").disabled = !disponible || !["DETENIDA", "PAUSADA"].includes(modo);
    elemento("iniciar").textContent = modo === "PAUSADA" ? "Reanudar" : "Iniciar";
    elemento("pausar").disabled = !disponible || modo !== "EN_CURSO";
    elemento("reiniciar").disabled = !disponible;
    elemento("aplicar").disabled = !disponible;
    campos.quantum.disabled = !disponible;
    campos.velocidadMs.disabled = !disponible;
    campos.memoriaTotal.disabled = !disponible || modo !== "DETENIDA";
    campos.politicaMemoria.disabled = !disponible || modo !== "DETENIDA";
    elemento("conexion").textContent = conectado ? "Conectado" : "Sin conexión";
    elemento("conexion").classList.toggle("desconectada", !conectado);
  }

  function actualizarConfiguracion(estado) {
    if (estado.modo !== "DETENIDA") {
      for (const nombre of ["memoriaTotal", "politicaMemoria"]) {
        cambios.delete(nombre);
        campos[nombre].value = estado.configuracion[nombre];
      }
    }
    for (const [nombre, control] of Object.entries(campos)) {
      // El sondeo no sustituye una edición en curso por el valor anterior del servidor.
      if (!cambios.has(nombre) && document.activeElement !== control) control.value = estado.configuracion[nombre];
    }
    elemento("valor-velocidad").textContent = `${campos.velocidadMs.value} ms`;
    elemento("planificador").textContent = estado.configuracion.planificador === "ROUND_ROBIN" ? "Round Robin" : estado.configuracion.planificador;
  }

  function renderizarColas(estado) {
    const colas = estado.colas;
    for (const [nombre, vacio] of [["nuevos", "Sin procesos en espera"], ["listos", "Sin procesos listos"], ["bloqueados", "Sin bloqueos de E/S"], ["terminados", "Ningún proceso ha terminado"]]) {
      const fragmento = document.createDocumentFragment();
      for (const proceso of colas[nombre]) {
        const id = typeof proceso === "string" ? proceso : proceso.id;
        const nodo = tarjeta(id);
        if (nombre === "nuevos") {
          nodo.append(crear("p", "", `Necesita ${proceso.memoria} MB · ${proceso.adelantamientos}/${estado.configuracion.maxAdelantamientos} adelantamientos`));
          let causa = `Mayor hueco contiguo: ${estado.memoria.mayorHueco} MB`;
          if (proceso.memoria > estado.memoria.mayorHueco) {
            if (proceso.memoria <= estado.memoria.libre) causa += " · Fragmentación externa";
          } else {
            causa += " · Admisión pendiente del próximo tick";
          }
          // Un protegido anterior impide admitir posteriores aunque su solicitud quepa.
          if (!proceso.bloqueante && colas.nuevos.slice(0, colas.nuevos.indexOf(proceso)).some((anterior) => anterior.bloqueante)) {
            causa = `Mayor hueco contiguo: ${estado.memoria.mayorHueco} MB · Admisión detenida por un proceso bloqueante anterior`;
          }
          nodo.append(crear("p", "", causa));
          if (proceso.bloqueante) {
            nodo.classList.add("bloqueante");
            nodo.append(crear("span", "proteccion", "Prioridad de admisión · Bloqueante"));
          }
        } else if (nombre === "bloqueados") {
          nodo.append(crear("p", "", `E/S restante: ${proceso.ticksRestantesES} ticks`));
        }
        fragmento.append(nodo);
      }
      if (!colas[nombre].length) fragmento.append(crear("li", "vacio", vacio));
      elemento(nombre).replaceChildren(fragmento);
      elemento(`cantidad-${nombre}`).textContent = colas[nombre].length;
    }
    const cpu = colas.ejecucion;
    elemento("cantidad-ejecucion").textContent = cpu ? "1" : "0";
    if (cpu) {
      const nodo = tarjeta(cpu.id);
      const contenedor = crear("div", nodo.className);
      contenedor.style.cssText = nodo.style.cssText;
      contenedor.append(...nodo.childNodes);
      contenedor.append(crear("p", "", `Quantum consumido: ${cpu.ticksQuantumUsados} ticks`));
      const proceso = estado.procesos.find((candidato) => candidato.id === cpu.id);
      contenedor.append(crear("p", "", `CPU acumulada: ${proceso.ticksConsumidos} · Restan ${proceso.ticksRestantes} ticks`));
      elemento("ejecucion").replaceChildren(contenedor);
    } else elemento("ejecucion").replaceChildren(crear("p", "vacio", "CPU ociosa"));
  }

  function renderizarMemoria(estado) {
    const memoria = estado.memoria;
    elemento("politica-vigente").textContent = politicas[estado.configuracion.politicaMemoria];
    for (const [id, valor] of [["ram-capacidad", memoria.total], ["ram-libre", memoria.libre], ["ram-mayor-hueco", memoria.mayorHueco], ["ram-fragmentacion", memoria.fragmentacionExterna]]) elemento(id).textContent = `${valor} MB`;
    const mapa = document.createDocumentFragment();
    const detalle = document.createDocumentFragment();
    const descripciones = [];
    for (const bloque of memoria.bloques) {
      const hueco = bloque.idProceso === null;
      const nombre = hueco ? "Hueco libre" : bloque.idProceso;
      const descripcion = `${nombre}: [${bloque.base}–${bloque.limite}] · ${bloque.tamano} MB`;
      const nodo = crear("div", `bloque-memoria${hueco ? " hueco" : ""}`);
      nodo.style.width = `${100 * bloque.tamano / memoria.total}%`;
      nodo.title = descripcion;
      nodo.append(crear("strong", "", hueco ? "Libre" : nombre), crear("span", "", `${bloque.base}–${bloque.limite}`), crear("span", "", `${bloque.tamano} MB`));
      if (!hueco) colorear(nodo, nombre);
      mapa.append(nodo);
      const etiqueta = crear("li", "", descripcion);
      if (!hueco) colorear(etiqueta, nombre);
      etiqueta.prepend(crear("i", "muestra-color"));
      detalle.append(etiqueta);
      descripciones.push(descripcion);
    }
    elemento("mapa-memoria").replaceChildren(mapa);
    elemento("mapa-memoria").setAttribute("aria-label", descripciones.join("; "));
    elemento("detalle-memoria").replaceChildren(detalle);
  }

  function renderizarGantt(estado) {
    const desplazamiento = elemento("desplazamiento-gantt");
    const seguirFinal = desplazamiento.scrollWidth - desplazamiento.clientWidth - desplazamiento.scrollLeft < 30;
    const total = Math.max(1, estado.instante);
    // El ancho mínimo por tick conserva legibles los turnos cortos; el contenedor permite recorrer todo el historial.
    elemento("gantt-vacio").hidden = estado.gantt.length > 0;
    desplazamiento.hidden = estado.gantt.length === 0;
    elemento("linea-tiempo").style.width = `${Math.max(desplazamiento.clientWidth, total * 32)}px`;
    const tramos = document.createDocumentFragment();
    for (const tramo of estado.gantt) {
      const nombre = tramo.idProceso ?? "Ocioso";
      const nodo = crear("div", `tramo-gantt${tramo.idProceso === null ? " ocioso" : ""}`, nombre);
      nodo.style.width = `${100 * (tramo.fin - tramo.inicio) / total}%`;
      nodo.title = `${nombre} [${tramo.inicio}, ${tramo.fin}) · ${tramo.fin - tramo.inicio} ticks`;
      if (tramo.idProceso !== null) colorear(nodo, nombre);
      tramos.append(nodo);
    }
    elemento("gantt").replaceChildren(tramos);
    elemento("gantt").setAttribute("aria-label", estado.gantt.map((tramo) => `${tramo.idProceso ?? "Ocioso"} [${tramo.inicio}, ${tramo.fin})`).join("; "));
    const escala = document.createDocumentFragment();
    const intervalo = Math.max(1, Math.ceil(total / 60));
    for (let instante = 0; instante <= total; instante += intervalo) {
      const marca = crear("span", "marca-tiempo", instante);
      marca.style.left = `${100 * instante / total}%`;
      escala.append(marca);
    }
    if (total % intervalo !== 0) {
      const marca = crear("span", "marca-tiempo", total);
      marca.style.left = "100%";
      escala.append(marca);
    }
    elemento("escala-gantt").replaceChildren(escala);
    if (seguirFinal) desplazamiento.scrollLeft = desplazamiento.scrollWidth;
    const leyenda = document.createDocumentFragment();
    for (const proceso of estado.procesos) {
      const etiqueta = crear("span", "", proceso.id);
      colorear(etiqueta, proceso.id);
      etiqueta.prepend(crear("i", "muestra-color"));
      leyenda.append(etiqueta);
    }
    leyenda.append(crear("span", "", "Gris: CPU ociosa"));
    elemento("leyenda-procesos").replaceChildren(leyenda);
  }

  function renderizarMetricas(estado) {
    elemento("espera-promedio").textContent = estado.metricas.esperaPromedio === null ? "—" : `${decimales.format(estado.metricas.esperaPromedio)} ticks`;
    elemento("retorno-promedio").textContent = estado.metricas.retornoPromedio === null ? "—" : `${decimales.format(estado.metricas.retornoPromedio)} ticks`;
    elemento("utilizacion-cpu").textContent = estado.metricas.utilizacionCpu === null ? "—" : `${porcentaje.format(estado.metricas.utilizacionCpu)} %`;
    elemento("resumen-terminados").textContent = `${estado.metricas.terminados} de ${estado.metricas.total} terminados`;
    const filas = document.createDocumentFragment();
    for (const proceso of estado.procesos) {
      const fila = crear("tr");
      const id = crear("th", "id-proceso", proceso.id);
      id.scope = "row";
      id.title = estados[proceso.estado];
      colorear(id, proceso.id);
      fila.append(id);
      for (const campo of ["tiempoLlegada", "tiempoRafaga", "instanteFinalizacion", "tiempoRetorno", "tiempoEspera", "ticksES"]) fila.append(crear("td", "", proceso[campo] ?? "—"));
      filas.append(fila);
    }
    elemento("tabla-metricas").replaceChildren(filas);
  }

  function agregarEventos(nuevos, reemplazar) {
    const lista = elemento("bitacora");
    const posicion = lista.scrollTop;
    const alturaAnterior = lista.scrollHeight;
    if (reemplazar || eventosRecibidos === 0) lista.replaceChildren();
    const fragmento = document.createDocumentFragment();
    for (const evento of nuevos.slice().reverse()) {
      const nodo = crear("li", "evento");
      colorear(nodo, evento.idProceso);
      const detalle = crear("div");
      detalle.append(crear("strong", "id-proceso", evento.idProceso), crear("p", "", `${estados[evento.origen]} → ${estados[evento.destino]}`), crear("p", "", evento.motivo));
      nodo.append(crear("time", "", `t=${evento.instante}`), detalle);
      fragmento.append(nodo);
    }
    lista.prepend(fragmento);
    if (!eventos.length) lista.replaceChildren(crear("li", "vacio", "Aún no hay eventos."));
    // Leer eventos anteriores no obliga a volver al principio cada 200 ms.
    if (!reemplazar && posicion > 0) lista.scrollTop = posicion + lista.scrollHeight - alturaAnterior;
    elemento("total-eventos").textContent = `${eventos.length} eventos`;
  }

  async function pedir(ruta, opciones = {}) {
    const cancelacion = new AbortController();
    const inicioPeticion = performance.now();
    const plazo = setTimeout(() => cancelacion.abort(), 2500);
    try {
      const respuesta = await fetch(ruta, { cache: "no-store", ...opciones, signal: cancelacion.signal });
      const cuerpo = await respuesta.json();
      // Datos inspeccionables para reproducir SC-004: incluyen descarga y análisis de JSON.
      if (ruta.startsWith("/api/estado")) {
        const latenciaMs = performance.now() - inicioPeticion;
        respuestasEstado++;
        maximaLatenciaEstadoMs = Math.max(maximaLatenciaEstadoMs, latenciaMs);
        elemento("conexion").dataset.respuestasEstado = respuestasEstado;
        elemento("conexion").dataset.latenciaEstadoMs = latenciaMs.toFixed(3);
        elemento("conexion").dataset.maximaLatenciaEstadoMs = maximaLatenciaEstadoMs.toFixed(3);
      }
      if (!respuesta.ok) {
        const error = new Error(cuerpo.mensaje || `Error HTTP ${respuesta.status}`);
        error.esRespuesta = true;
        throw error;
      }
      return cuerpo;
    } finally { clearTimeout(plazo); }
  }

  async function asimilar(estado, desde = 0) {
    let reemplazar = desde === 0;
    // Si el reinicio invalida nuestro índice, recuperamos la bitácora completa antes de renderizar.
    if (estado.totalEventos < eventosRecibidos || (desde !== 0 && desde + estado.bitacora.length !== estado.totalEventos)) {
      estado = await pedir("/api/estado?desdeEvento=0");
      reemplazar = true;
    }
    if (ultimaInstantanea && estado.instante < ultimaInstantanea.instante) cambios.clear();
    if (reemplazar) eventos = estado.bitacora.slice();
    else eventos.push(...estado.bitacora);
    agregarEventos(estado.bitacora, reemplazar);
    eventosRecibidos = estado.totalEventos;
    if (estado.modo === "FINALIZADA" && ultimaInstantanea?.modo !== "FINALIZADA") {
      mostrarMensaje("Simulación finalizada. Puedes consultar las métricas o reiniciar el escenario.");
    }
    ultimaInstantanea = estado;
    conectado = true;
    elemento("instante").textContent = estado.instante;
    elemento("modo").textContent = estado.modo;
    elemento("modo").dataset.modo = estado.modo;
    elemento("aviso").textContent = estado.aviso ?? "";
    elemento("aviso").hidden = !estado.aviso;
    actualizarConfiguracion(estado);
    renderizarColas(estado);
    renderizarMemoria(estado);
    renderizarGantt(estado);
    renderizarMetricas(estado);
    actualizarControles();
    if (elemento("mensaje").dataset.conexion === "fallida") {
      mostrarMensaje("");
      delete elemento("mensaje").dataset.conexion;
    }
  }

  function manejarError(error) {
    if (error.esRespuesta) mostrarMensaje(error.message, true);
    else {
      conectado = false;
      mostrarMensaje("Sin conexión con el simulador. Reintentando…", true);
      elemento("mensaje").dataset.conexion = "fallida";
    }
    actualizarControles();
  }

  // Serializar GET y POST impide que una respuesta anterior deshaga visualmente una acción reciente.
  function encolar(accion) {
    const resultado = secuencia.then(accion);
    secuencia = resultado.catch(() => {});
    return resultado;
  }

  function sondear() {
    if (sondeoPendiente) return;
    sondeoPendiente = true;
    encolar(async () => {
      try {
        const desde = eventosRecibidos;
        await asimilar(await pedir(`/api/estado?desdeEvento=${desde}`), desde);
      } catch (error) { manejarError(error); }
    }).finally(() => { sondeoPendiente = false; });
  }

  async function actuar(ruta, cuerpo, mensaje) {
    if (accionPendiente || !conectado) return;
    accionPendiente = true;
    actualizarControles();
    await encolar(async () => {
      try {
        const estado = await pedir(ruta, { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, ...(cuerpo === undefined ? {} : { body: JSON.stringify(cuerpo) }) });
        if (cuerpo) for (const nombre of Object.keys(cuerpo)) cambios.delete(nombre);
        if (ruta.endsWith("/reiniciar")) cambios.clear();
        await asimilar(estado);
        mostrarMensaje(mensaje);
      } catch (error) { manejarError(error); }
      finally { accionPendiente = false; actualizarControles(); }
    });
  }

  elemento("iniciar").addEventListener("click", () => actuar("/api/control/iniciar", undefined, "Simulación en curso."));
  elemento("pausar").addEventListener("click", () => actuar("/api/control/pausar", undefined, "Simulación pausada."));
  elemento("reiniciar").addEventListener("click", () => actuar("/api/control/reiniciar", undefined, "Escenario reiniciado en el instante 0."));
  for (const [nombre, control] of Object.entries(campos)) control.addEventListener("input", () => cambios.add(nombre));
  campos.velocidadMs.addEventListener("input", () => { elemento("valor-velocidad").textContent = `${campos.velocidadMs.value} ms`; });
  campos.velocidadMs.addEventListener("change", () => actuar("/api/configuracion", { velocidadMs: Number(campos.velocidadMs.value) }, "Velocidad actualizada."));
  elemento("configuracion").addEventListener("submit", (evento) => {
    evento.preventDefault();
    const cuerpo = {};
    for (const nombre of ["quantum", "memoriaTotal", "politicaMemoria"]) {
      if (cambios.has(nombre) && !campos[nombre].disabled) cuerpo[nombre] = nombre === "politicaMemoria" ? campos[nombre].value : Number(campos[nombre].value);
    }
    if (!Object.keys(cuerpo).length) { mostrarMensaje("No hay cambios de configuración pendientes."); return; }
    actuar("/api/configuracion", cuerpo, "Configuración aplicada. El quantum se usará desde el siguiente despacho.");
  });
  window.addEventListener("resize", () => { if (ultimaInstantanea) renderizarGantt(ultimaInstantanea); });
  sondear();
  setInterval(sondear, 200);
})();
