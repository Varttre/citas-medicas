/* JavaScript mínimo de las pantallas. El menú lo dibuja el servidor (fragmentos/barra.html). */

/* Contador del bloqueo temporal: <span data-cuenta-regresiva="299"></span> muestra m:ss */
function iniciarCuentasRegresivas() {
  document.querySelectorAll('[data-cuenta-regresiva]').forEach(el => {
    let s = Number(el.dataset.cuentaRegresiva);
    const pintar = () => { el.textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
    pintar();
    const t = setInterval(() => { s = Math.max(0, s - 1); pintar(); if (!s) clearInterval(t); }, 1000);
  });
}

/* Mostrar u ocultar bloques según la opción elegida:
   <input type="radio|checkbox" data-muestra="#id" data-oculta="#otro"> */
function iniciarAlternancias() {
  const aplicar = input => {
    if (input.type === 'checkbox') {
      document.querySelectorAll(input.dataset.muestra || '').forEach(e => e.classList.toggle('d-none', !input.checked));
      return;
    }
    if (!input.checked) return;
    document.querySelectorAll(input.dataset.muestra || '').forEach(e => e.classList.remove('d-none'));
    document.querySelectorAll(input.dataset.oculta || '').forEach(e => e.classList.add('d-none'));
  };
  document.querySelectorAll('[data-muestra], [data-oculta]').forEach(input => {
    input.addEventListener('change', () => aplicar(input));
    aplicar(input);
  });
}

/* «Lo más pronto posible»: <button data-lo-mas-pronto="#dias"> pone en el campo su máximo (los días que faltan). */
function iniciarLoMasPronto() {
  document.querySelectorAll('[data-lo-mas-pronto]').forEach(b => b.addEventListener('click', () => {
    const campo = document.querySelector(b.dataset.loMasPronto);
    if (campo && campo.max) campo.value = campo.max;
  }));
}

/* Modo oscuro: botón flotante en todas las pantallas; la elección se guarda en este navegador. El tema se aplica
   antes de pintar con el script de la cabecera (fragmentos/base.html), para que no parpadee. */
function iniciarTema() {
  const raiz = document.documentElement;
  const boton = document.createElement('button');
  boton.id = 'cambiar-tema';
  boton.type = 'button';
  boton.className = 'btn btn-primary';
  const pintar = () => {
    const oscuro = raiz.getAttribute('data-bs-theme') === 'dark';
    boton.innerHTML = oscuro ? '<i class="bi bi-sun"></i>' : '<i class="bi bi-moon-stars"></i>';
    boton.title = oscuro ? 'Modo claro' : 'Modo oscuro';
    boton.setAttribute('aria-label', boton.title);
  };
  boton.addEventListener('click', () => {
    const nuevo = raiz.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    raiz.setAttribute('data-bs-theme', nuevo);
    try { localStorage.setItem('tema', nuevo); } catch (e) { /* sin almacenamiento: dura hasta recargar */ }
    pintar();
  });
  pintar();
  document.body.appendChild(boton);
}

/* Fondo de patrón (Mi perfil): marca la opción guardada y aplica la nueva al momento. La cabecera lo aplica antes de
   pintar; si no hay nada guardado, «puntos». */
function iniciarFondo() {
  const raiz = document.documentElement;
  document.querySelectorAll('input[name="fondo"]').forEach(radio => {
    radio.checked = radio.value === raiz.getAttribute('data-fondo');
    radio.addEventListener('change', () => {
      raiz.setAttribute('data-fondo', radio.value);
      try { localStorage.setItem('fondo', radio.value); } catch (e) { /* sin almacenamiento: dura hasta recargar */ }
    });
  });
}

/* Filtros cruzados médico ↔ especialidad: <select data-filtro-especialidad="id-del-select-de-especialidad"> con
   opciones data-esp="1 3". Al elegir una especialidad solo quedan los médicos que la atienden; al elegir un médico,
   solo sus especialidades. Si lo elegido deja de encajar, vuelve a «todos». */
function iniciarFiltrosMedicoEspecialidad() {
  document.querySelectorAll('select[data-filtro-especialidad]').forEach(selMedico => {
    const selEsp = document.getElementById(selMedico.dataset.filtroEspecialidad);
    if (!selEsp) { return; }
    const mostrar = (opcion, visible) => { opcion.hidden = !visible; opcion.disabled = !visible; };
    const especialidadesDe = opcion => (opcion && opcion.dataset.esp ? opcion.dataset.esp.split(' ') : null);
    const aplicar = () => {
      const esp = selEsp.value;
      Array.from(selMedico.options).forEach(o => {
        if (o.value) { mostrar(o, !esp || especialidadesDe(o).includes(esp)); }
      });
      if (selMedico.selectedOptions[0] && selMedico.selectedOptions[0].hidden) { selMedico.value = ''; }
      const delMedico = selMedico.value ? especialidadesDe(selMedico.selectedOptions[0]) : null;
      Array.from(selEsp.options).forEach(o => {
        if (o.value) { mostrar(o, !delMedico || delMedico.includes(o.value)); }
      });
    };
    selEsp.addEventListener('change', aplicar);
    selMedico.addEventListener('change', aplicar);
    aplicar();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  iniciarCuentasRegresivas(); iniciarAlternancias(); iniciarTema(); iniciarFondo(); iniciarFiltrosMedicoEspecialidad();
  iniciarLoMasPronto();
});
