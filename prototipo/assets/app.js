/* Prototipo navegable · Sistema de Gestión de Citas y Recuperación de Agenda
   Dibuja la barra lateral de cada perfil: una barra permanente que indica en qué sección estás;
   solo cambia el contenido del main.
   Cada página declara <body data-perfil="…" data-seccion="…">.
   Fecha de demostración: miércoles 11 de noviembre de 2026. */

const CONSULTORIO = 'Consultorio San Isidro';

const RECEPCION = [
  { id: 'inicio',         texto: 'Inicio',           icono: 'house',            href: 'recepcion-inicio.html' },
  { id: 'agenda',         texto: 'Agenda',           icono: 'calendar3',        href: 'recepcion-agenda.html' },
  { id: 'nueva-cita',     texto: 'Nueva cita',       icono: 'plus-circle',      href: 'recepcion-nueva-cita.html' },
  { id: 'confirmaciones', texto: 'Confirmaciones',   icono: 'telephone',        href: 'recepcion-confirmaciones.html', badge: 14 },
  { id: 'cupos',          texto: 'Cupos liberados',  icono: 'calendar2-plus',   href: 'recepcion-cupos.html', badge: 3 },
  { id: 'pendientes',     texto: 'Citas pendientes', icono: 'clipboard-check',  href: 'recepcion-pendientes.html', badge: 7 },
  { id: 'pacientes',      texto: 'Pacientes',        icono: 'people',           href: 'recepcion-pacientes.html' },
];

const PERFILES = {
  paciente: {
    usuario: 'Lucía Vega Ríos', rol: 'Paciente',
    menu: [
      { id: 'inicio',     texto: 'Inicio',                   icono: 'house',     href: 'paciente-inicio.html' },
      { id: 'nueva-cita', texto: 'Nueva cita',               icono: 'plus-circle', href: 'paciente-nueva-cita-1.html' },
      { id: 'mis-citas',  texto: 'Mis citas',                icono: 'calendar-check', href: 'paciente-mis-citas.html', badge: 3 },
      { id: 'avisos',     texto: 'Avisos',                   icono: 'megaphone', href: 'paciente-avisos.html' },
      { id: 'whatsapp',   texto: 'WhatsApp del consultorio', icono: 'whatsapp',  href: 'paciente-whatsapp.html' },
    ],
    perfil: 'paciente-perfil.html',
  },
  medico: {
    usuario: 'Dra. Carmen Ramírez', rol: 'Médico · Cardiología',
    menu: [
      { id: 'inicio',       texto: 'Inicio',          icono: 'house',        href: 'medico-inicio.html' },
      { id: 'agenda',       texto: 'Mi agenda',       icono: 'calendar3',    href: 'medico-agenda.html' },
      { id: 'horario',      texto: 'Mi horario',      icono: 'clock',        href: 'medico-horario.html' },
      { id: 'estadisticas', texto: 'Mis estadísticas', icono: 'bar-chart',   href: 'medico-estadisticas.html' },
    ],
  },
  recepcion: {
    usuario: 'Ana Quispe', rol: 'Recepción',
    menu: RECEPCION,
  },
  admin: {
    usuario: 'Jorge Salinas', rol: 'Administrador',
    menu: [
      ...RECEPCION,
      { separador: 'Administración' },
      { id: 'configuracion', texto: 'Configuración', icono: 'sliders',     href: 'admin-configuracion.html' },
      { id: 'usuarios',      texto: 'Usuarios',      icono: 'person-gear', href: 'admin-usuarios.html' },
      { id: 'reportes',      texto: 'Reportes',      icono: 'graph-up',    href: 'admin-reportes.html' },
    ],
  },
};

function itemMenu(item, seccion) {
  if (item.separador) {
    return `<li class="nav-item mt-3 mb-1 px-3 small text-uppercase text-body-secondary fw-semibold">${item.separador}</li>`;
  }
  const activo = item.id === seccion;
  const badge = item.badge
    ? `<span class="badge rounded-pill ${activo ? 'text-bg-light' : 'text-bg-secondary'} ms-auto">${item.badge}</span>` : '';
  return `<li class="nav-item">
      <a class="nav-link d-flex align-items-center gap-2 ${activo ? 'active' : 'link-body-emphasis'}"
         href="${item.href}" ${activo ? 'aria-current="page"' : ''}>
        <i class="bi bi-${item.icono}"></i><span>${item.texto}</span>${badge}
      </a></li>`;
}

/* El administrador usa las pantallas de recepción: el prototipo recuerda que entró como
   administrador para seguir mostrándole su menú. Solo es una comodidad de navegación. */
function perfilEfectivo(perfil) {
  try {
    if (!perfil) { sessionStorage.removeItem('perfil'); return perfil; }
    if (perfil === 'admin') sessionStorage.setItem('perfil', 'admin');
    else if (perfil !== 'recepcion') sessionStorage.removeItem('perfil');
    if (perfil === 'recepcion' && sessionStorage.getItem('perfil') === 'admin') return 'admin';
  } catch (e) { /* sin almacenamiento: se usa el perfil de la página */ }
  return perfil;
}

function dibujarBarraLateral() {
  const { seccion } = document.body.dataset;
  const perfil = perfilEfectivo(document.body.dataset.perfil);
  const conf = PERFILES[perfil];
  const destino = document.getElementById('barra-lateral');
  if (!conf || !destino) return;

  const miPerfil = conf.perfil
    ? `<li><a class="dropdown-item" href="${conf.perfil}"><i class="bi bi-person me-2"></i>Mi perfil</a></li>` : '';

  destino.innerHTML = `
    <div class="offcanvas-header border-bottom">
      <span class="fw-semibold"><i class="bi bi-heart-pulse text-primary me-2"></i>${CONSULTORIO}</span>
      <button type="button" class="btn-close" data-bs-dismiss="offcanvas" data-bs-target="#barra-lateral" aria-label="Cerrar"></button>
    </div>
    <div class="offcanvas-body d-flex flex-column p-0">
      <a href="index.html" class="d-none d-lg-flex align-items-center gap-2 px-3 py-3 border-bottom text-decoration-none link-body-emphasis">
        <i class="bi bi-heart-pulse fs-4 text-primary"></i>
        <span class="fw-semibold lh-sm">${CONSULTORIO}</span>
      </a>
      <ul class="nav nav-pills flex-column gap-1 p-2 flex-grow-1">
        ${conf.menu.map(i => itemMenu(i, seccion)).join('')}
      </ul>
      <div class="dropdown border-top p-2">
        <a href="#" class="d-flex align-items-center gap-2 p-2 rounded text-decoration-none link-body-emphasis dropdown-toggle ${seccion === 'cuenta' ? 'bg-body-secondary' : ''}"
           data-bs-toggle="dropdown" aria-expanded="false">
          <i class="bi bi-person-circle fs-4"></i>
          <span class="lh-sm"><span class="d-block fw-semibold small">${conf.usuario}</span>
          <span class="d-block small text-body-secondary">${conf.rol}</span></span>
        </a>
        <ul class="dropdown-menu shadow-sm">
          ${miPerfil}
          <li><a class="dropdown-item" href="login.html"><i class="bi bi-box-arrow-right me-2"></i>Cerrar sesión</a></li>
        </ul>
      </div>
    </div>`;
}

document.addEventListener('DOMContentLoaded', dibujarBarraLateral);

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

/* ?captura oculta el botón del índice para tomar capturas de pantalla */
document.addEventListener('DOMContentLoaded', () => {
  if (new URLSearchParams(location.search).has('captura')) {
    document.querySelectorAll('.indice-prototipo').forEach(e => e.remove());
  }
});
