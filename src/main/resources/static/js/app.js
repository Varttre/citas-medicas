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

document.addEventListener('DOMContentLoaded', () => { iniciarCuentasRegresivas(); iniciarAlternancias(); });
