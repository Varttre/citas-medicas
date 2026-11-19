package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.AvisoForm;
import pe.consultorio.citas.dto.EspecialidadForm;
import pe.consultorio.citas.dto.ParametrosForm;
import pe.consultorio.citas.dto.PlazosForm;
import pe.consultorio.citas.dto.TipoConsultaForm;
import pe.consultorio.citas.entity.Aviso;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.ParametroConsultorio;
import pe.consultorio.citas.entity.PlazoTransicion;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.Transicion;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.AvisoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.ParametroConsultorioRepository;
import pe.consultorio.citas.repository.PlazoTransicionRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/**
Configuración del consultorio: la fila única de parámetros, los plazos de cada transición y los catálogos (tipos de
consulta, especialidades y avisos). La lectura la usan todos los servicios; la escritura, solo la pantalla del
administrador. Cada cambio guardado deja una auditoría CONFIG_CAMBIO con «campo: anterior → nuevo».
*/
@Service
@Transactional(readOnly = true)
public class ConfiguracionService {

    private static final short ID_PARAMETROS = 1;

    private final ParametroConsultorioRepository parametros;
    private final PlazoTransicionRepository plazos;
    private final TipoConsultaRepository tipos;
    private final EspecialidadRepository especialidades;
    private final AvisoRepository avisos;
    private final CitaRepository citas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public ConfiguracionService(ParametroConsultorioRepository parametros, PlazoTransicionRepository plazos,
            TipoConsultaRepository tipos, EspecialidadRepository especialidades, AvisoRepository avisos,
            CitaRepository citas, AuditoriaService auditoria, Clock reloj) {
        this.parametros = parametros;
        this.plazos = plazos;
        this.tipos = tipos;
        this.especialidades = especialidades;
        this.avisos = avisos;
        this.citas = citas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** La fila de parámetros (id_parametro = 1). */
    public ParametroConsultorio parametros() {
        return parametros.findById(ID_PARAMETROS)
            .orElseThrow(() -> new IllegalStateException("Faltan los parámetros del consultorio (fila 1)"));
    }

    /** Ventana de tiempo en la que se permite la transición. */
    public PlazoTransicion plazo(Transicion t) {
        return plazos.findById(t)
            .orElseThrow(() -> new RecursoNoEncontradoException("No hay plazo configurado para " + t));
    }

    /** Tipos de consulta activos, por nombre. */
    public List<TipoConsulta> tiposActivos() {
        return tipos.findAll().stream()
            .filter(TipoConsulta::isActivo)
            .sorted(Comparator.comparing(TipoConsulta::getNombre))
            .toList();
    }

    /** Tipos activos que el paciente puede reservar desde el portal. */
    public List<TipoConsulta> tiposWeb() {
        return tiposActivos().stream().filter(TipoConsulta::isReservableWeb).toList();
    }

    /** Especialidades activas, por nombre. */
    public List<Especialidad> especialidadesActivas() {
        return especialidades.findAll().stream()
            .filter(Especialidad::isActivo)
            .sorted(Comparator.comparing(Especialidad::getNombre))
            .toList();
    }

    /** Paso de la rejilla en minutos. */
    public int incremento() {
        return parametros().getIncrementoMin();
    }

    /** El hueco más chico que todavía sirve para algo: la menor duración por defecto entre los tipos activos. */
    public int huecoMinimoUtil() {
        return tiposActivos().stream()
            .mapToInt(TipoConsulta::getDuracionDefecto)
            .min()
            .orElse(incremento());
    }

    // ------------------------------------------------------------ Listas completas para la pantalla

    /** Todos los tipos de consulta, activos primero y por nombre. */
    public List<TipoConsulta> todosLosTipos() {
        return tipos.findAll().stream()
            .sorted(Comparator.comparing((TipoConsulta t) -> !t.isActivo()).thenComparing(TipoConsulta::getNombre))
            .toList();
    }

    /** Todas las especialidades, por nombre. */
    public List<Especialidad> todasLasEspecialidades() {
        return especialidades.findAll().stream().sorted(Comparator.comparing(Especialidad::getNombre)).toList();
    }

    /** Todos los avisos del portal, los más nuevos primero. */
    public List<Aviso> todosLosAvisos() {
        return avisos.findAll().stream().sorted(Comparator.comparing(Aviso::getIdAviso).reversed()).toList();
    }

    /** Avisos visibles en el portal del paciente. */
    public List<Aviso> avisosActivos() {
        return todosLosAvisos().stream().filter(Aviso::isActivo).toList();
    }

    /** Citas futuras aún vigentes (programadas o confirmadas) del tipo, las más próximas primero. */
    public List<Cita> citasFuturasDelTipo(Short idTipo) {
        return citas.delTipoDesde(idTipo, ahora(), List.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA));
    }

    // ------------------------------------------------------------ Escritura

    /**
    Crea ({@code id} nulo) o edita un tipo de consulta. Desactivar un tipo con citas futuras exige
    {@code confirmado}; esas citas no cambian. El tipo por defecto del portal debe seguir activo y reservable por web.
    */
    @Transactional
    public TipoConsulta guardarTipo(Short id, TipoConsultaForm f, boolean confirmado, Long actor) {
        String nombre = texto(f.nombre(), "el nombre", 60);
        int duracion = enRango(f.duracionDefecto(), 15, 240, "La duración por defecto");
        if (duracion % 15 != 0) {
            throw new ReglaNegocioException("La duración por defecto debe ser múltiplo de 15 minutos.");
        }
        if (f.incrementoMin() != null && (f.incrementoMin() <= 0 || f.incrementoMin() % 15 != 0
                || f.incrementoMin() > 60)) {
            throw new ReglaNegocioException("El incremento propio debe ser 15, 30, 45 o 60 minutos.");
        }
        if (f.modalidadPermitida() == null) {
            throw new ReglaNegocioException("Elige la modalidad.");
        }
        boolean activo = si(f.activo());
        boolean web = si(f.reservableWeb());
        tipos.findAll().stream()
            .filter(t -> t.getNombre().equalsIgnoreCase(nombre) && !t.getIdTipoConsulta().equals(id))
            .findFirst()
            .ifPresent(t -> {
                throw new ReglaNegocioException("Ya existe un tipo de consulta con ese nombre.");
            });

        TipoConsulta t = id == null ? new TipoConsulta() : tipos.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de consulta inexistente " + id));
        if (id != null && Objects.equals(parametros().getTipoWebDefecto().getIdTipoConsulta(), id) && (!activo || !web)) {
            throw new ReglaNegocioException("Es el tipo por defecto del portal: debe seguir activo y reservable por web.");
        }
        if (id != null && t.isActivo() && !activo && !confirmado) {
            int futuras = citasFuturasDelTipo(id).size();
            if (futuras > 0) {
                throw new ConfirmacionRequeridaException(futuras);
            }
        }
        List<String> cambios = new ArrayList<>();
        cambio(cambios, "nombre", t.getNombre(), nombre);
        cambio(cambios, "duracion_defecto", t.getDuracionDefecto(), (short) duracion);
        cambio(cambios, "incremento_min", t.getIncrementoMin(), f.incrementoMin());
        cambio(cambios, "ajuste_medico", id == null ? null : t.isAjusteMedico(), si(f.ajusteMedico()));
        cambio(cambios, "ajuste_recepcion", id == null ? null : t.isAjusteRecepcion(), si(f.ajusteRecepcion()));
        cambio(cambios, "reservable_web", id == null ? null : t.isReservableWeb(), web);
        cambio(cambios, "modalidad_permitida", t.getModalidadPermitida(), f.modalidadPermitida());
        cambio(cambios, "activo", id == null ? null : t.isActivo(), activo);
        t.setNombre(nombre);
        t.setDuracionDefecto((short) duracion);
        t.setIncrementoMin(f.incrementoMin());
        t.setAjusteMedico(si(f.ajusteMedico()));
        t.setAjusteRecepcion(si(f.ajusteRecepcion()));
        t.setReservableWeb(web);
        t.setModalidadPermitida(f.modalidadPermitida());
        t.setActivo(activo);
        t = tipos.save(t);
        auditar(actor, "TIPO_CONSULTA", String.valueOf(t.getIdTipoConsulta()), cambios);
        return t;
    }

    /** Desactiva un tipo de consulta ya confirmado por el administrador; sus citas futuras siguen igual. */
    @Transactional
    public void desactivarTipo(Short id, Long actor) {
        TipoConsulta t = tipos.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de consulta inexistente " + id));
        guardarTipo(id, new TipoConsultaForm(t.getNombre(), t.getDuracionDefecto(), t.getIncrementoMin(),
            t.isAjusteMedico(), t.isAjusteRecepcion(), t.isReservableWeb(), t.getModalidadPermitida(), false), true,
            actor);
    }

    /** Guarda los parámetros globales con las mismas reglas que exige la base. */
    @Transactional
    public void guardarParametros(ParametrosForm f, Long actor) {
        ParametroConsultorio p = parametros();
        short protegidos = (short) enRango(f.minutosProtegidos(), 0, 480, "Los minutos protegidos");
        short ventana = (short) enRango(f.ventanaLiberacionH(), 0, 168, "La ventana de liberación");
        short bloqueo = (short) enRango(f.bloqueoTemporalMin(), 1, 30, "El bloqueo temporal");
        short nVentana = (short) enRango(f.nVentanaEstimacion(), 1, 200, "Las citas de la ventana de estimación");
        short recordar = (short) enRango(f.diasRecordarSugerencia(), 1, 90, "Los días de «Recordar después»");
        short anticipacion = (short) enRango(f.anticipacionConfirmH(), 0, 168, "La anticipación de la confirmación");
        short vigencia = (short) enRango(f.vigenciaIndicadaDias(), 1, 365, "La vigencia de una cita indicada");
        short intentos = (short) enRango(f.maxIntentosLogin(), 1, 20, "Los intentos de inicio de sesión");
        short minutosBloqueo = (short) enRango(f.minutosBloqueoLogin(), 1, 1440, "La duración del bloqueo");
        boolean penalizacion = si(f.mostrarPenalizacion());
        String textoPenalizacion = vacio(f.textoPenalizacion()) ? null : f.textoPenalizacion().trim();
        if (penalizacion && textoPenalizacion == null) {
            throw new ReglaNegocioException("Escribe el texto del aviso de penalización o desactívalo.");
        }
        if (textoPenalizacion != null && textoPenalizacion.length() > 255) {
            throw new ReglaNegocioException("El texto de penalización admite hasta 255 caracteres.");
        }
        String whatsapp = vacio(f.whatsappNumero()) ? null : f.whatsappNumero().trim();
        if (whatsapp != null && !whatsapp.matches("\\+?[0-9 ]{6,20}")) {
            throw new ReglaNegocioException("El número de WhatsApp solo admite dígitos, espacios y un «+» inicial.");
        }
        TipoConsulta tipoWeb = f.idTipoWebDefecto() == null ? null : tipos.findById(f.idTipoWebDefecto()).orElse(null);
        if (tipoWeb == null || !tipoWeb.isActivo() || !tipoWeb.isReservableWeb()) {
            throw new ReglaNegocioException("El tipo por defecto del portal debe estar activo y ser reservable por web.");
        }

        List<String> cambios = new ArrayList<>();
        cambio(cambios, "minutos_protegidos", p.getMinutosProtegidos(), protegidos);
        cambio(cambios, "ventana_liberacion_h", p.getVentanaLiberacionH(), ventana);
        cambio(cambios, "bloqueo_temporal_min", p.getBloqueoTemporalMin(), bloqueo);
        cambio(cambios, "n_ventana_estimacion", p.getNVentanaEstimacion(), nVentana);
        cambio(cambios, "dias_recordar_sugerencia", p.getDiasRecordarSugerencia(), recordar);
        cambio(cambios, "anticipacion_confirm_h", p.getAnticipacionConfirmH(), anticipacion);
        cambio(cambios, "vigencia_indicada_dias", p.getVigenciaIndicadaDias(), vigencia);
        cambio(cambios, "mostrar_penalizacion", p.isMostrarPenalizacion(), penalizacion);
        cambio(cambios, "texto_penalizacion", p.getTextoPenalizacion(), textoPenalizacion);
        cambio(cambios, "id_tipo_web_defecto", p.getTipoWebDefecto().getNombre(), tipoWeb.getNombre());
        cambio(cambios, "whatsapp_numero", p.getWhatsappNumero(), whatsapp);
        cambio(cambios, "max_intentos_login", p.getMaxIntentosLogin(), intentos);
        cambio(cambios, "minutos_bloqueo_login", p.getMinutosBloqueoLogin(), minutosBloqueo);
        p.setMinutosProtegidos(protegidos);
        p.setVentanaLiberacionH(ventana);
        p.setBloqueoTemporalMin(bloqueo);
        p.setNVentanaEstimacion(nVentana);
        p.setDiasRecordarSugerencia(recordar);
        p.setAnticipacionConfirmH(anticipacion);
        p.setVigenciaIndicadaDias(vigencia);
        p.setMostrarPenalizacion(penalizacion);
        p.setTextoPenalizacion(textoPenalizacion);
        p.setTipoWebDefecto(tipoWeb);
        p.setWhatsappNumero(whatsapp);
        p.setMaxIntentosLogin(intentos);
        p.setMinutosBloqueoLogin(minutosBloqueo);
        parametros.save(p);
        auditar(actor, "PARAMETRO_CONSULTORIO", "1", cambios);
    }

    /** Guarda los tres plazos editables; los demás («fin del día», «tras asistió») son reglas fijas. */
    @Transactional
    public void guardarPlazos(PlazosForm f, Long actor) {
        short noAsistio = (short) enRango(f.noAsistioDespues(), 0, 240, "El plazo de «no asistió»");
        short cancelar = (short) enRango(f.cancelarAntes(), 0, 2880, "El plazo para cancelar");
        short llegada = (short) enRango(f.llegadaAntes(), 0, 240, "El plazo para registrar la llegada");
        List<String> cambios = new ArrayList<>();
        PlazoTransicion pNoAsistio = plazo(Transicion.NO_ASISTIO);
        cambio(cambios, "NO_ASISTIO.minutos_desde", pNoAsistio.getMinutosDesde(), noAsistio);
        pNoAsistio.setMinutosDesde(noAsistio);
        PlazoTransicion pCancelar = plazo(Transicion.CANCELADA_POR_PACIENTE);
        cambio(cambios, "CANCELADA_POR_PACIENTE.minutos_hasta", pCancelar.getMinutosHasta(), (short) -cancelar);
        pCancelar.setMinutosHasta((short) -cancelar);
        PlazoTransicion pLlegada = plazo(Transicion.ASISTIO);
        cambio(cambios, "ASISTIO.minutos_desde", pLlegada.getMinutosDesde(), (short) -llegada);
        pLlegada.setMinutosDesde((short) -llegada);
        plazos.saveAll(List.of(pNoAsistio, pCancelar, pLlegada));
        auditar(actor, "PLAZO_TRANSICION", null, cambios);
    }

    /** Crea ({@code id} nulo) o edita una especialidad. */
    @Transactional
    public Especialidad guardarEspecialidad(Short id, EspecialidadForm f, Long actor) {
        String nombre = texto(f.nombre(), "el nombre", 80);
        String descripcion = vacio(f.descripcion()) ? null : f.descripcion().trim();
        if (descripcion != null && descripcion.length() > 255) {
            throw new ReglaNegocioException("La descripción admite hasta 255 caracteres.");
        }
        especialidades.findAll().stream()
            .filter(e -> e.getNombre().equalsIgnoreCase(nombre) && !e.getIdEspecialidad().equals(id))
            .findFirst()
            .ifPresent(e -> {
                throw new ReglaNegocioException("Ya existe una especialidad con ese nombre.");
            });
        Especialidad e = id == null ? new Especialidad() : especialidades.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad inexistente " + id));
        List<String> cambios = new ArrayList<>();
        cambio(cambios, "nombre", e.getNombre(), nombre);
        cambio(cambios, "descripcion", e.getDescripcion(), descripcion);
        cambio(cambios, "activo", id == null ? null : e.isActivo(), si(f.activo()));
        e.setNombre(nombre);
        e.setDescripcion(descripcion);
        e.setActivo(si(f.activo()));
        e = especialidades.save(e);
        auditar(actor, "ESPECIALIDAD", String.valueOf(e.getIdEspecialidad()), cambios);
        return e;
    }

    /** Crea ({@code id} nulo) o edita un aviso del portal. */
    @Transactional
    public Aviso guardarAviso(Long id, AvisoForm f, Long actor) {
        String banner = texto(f.bannerUrl(), "la dirección de la imagen", 255);
        String titulo = texto(f.titulo(), "el título", 100);
        String cuerpo = texto(f.texto(), "el texto", 300);
        String enlace = texto(f.enlace(), "el enlace", 255);
        Aviso a = id == null ? new Aviso() : avisos.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Aviso inexistente " + id));
        List<String> cambios = new ArrayList<>();
        cambio(cambios, "banner_url", a.getBannerUrl(), banner);
        cambio(cambios, "titulo", a.getTitulo(), titulo);
        cambio(cambios, "texto", a.getTexto(), cuerpo);
        cambio(cambios, "enlace", a.getEnlace(), enlace);
        cambio(cambios, "activo", id == null ? null : a.isActivo(), si(f.activo()));
        a.setBannerUrl(banner);
        a.setTitulo(titulo);
        a.setTexto(cuerpo);
        a.setEnlace(enlace);
        a.setActivo(si(f.activo()));
        a = avisos.save(a);
        auditar(actor, "AVISO", String.valueOf(a.getIdAviso()), cambios);
        return a;
    }

    // ------------------------------------------------------------ Apoyo

    /** Desactivar el tipo deja citas futuras: el administrador debe confirmarlo. */
    public static class ConfirmacionRequeridaException extends ReglaNegocioException {
        private final int citasFuturas;

        public ConfirmacionRequeridaException(int citasFuturas) {
            super("Hay " + citasFuturas + " citas futuras de este tipo.");
            this.citasFuturas = citasFuturas;
        }

        public int getCitasFuturas() {
            return citasFuturas;
        }
    }

    private void auditar(Long actor, String entidad, String id, List<String> cambios) {
        if (!cambios.isEmpty()) {
            auditoria.registrar(actor, AccionAuditoria.CONFIG_CAMBIO, entidad, id, String.join("; ", cambios), null);
        }
    }

    private static void cambio(List<String> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": " + (anterior == null ? "—" : anterior) + " → " + (nuevo == null ? "—" : nuevo));
        }
    }

    private static String texto(String valor, String campo, int maximo) {
        if (vacio(valor)) {
            throw new ReglaNegocioException("Escribe " + campo + ".");
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new ReglaNegocioException("El campo " + campo + " admite hasta " + maximo + " caracteres.");
        }
        return limpio;
    }

    private static int enRango(Short valor, int minimo, int maximo, String campo) {
        if (valor == null || valor < minimo || valor > maximo) {
            throw new ReglaNegocioException(campo + " debe estar entre " + minimo + " y " + maximo + ".");
        }
        return valor;
    }

    private static boolean si(Boolean b) {
        return Boolean.TRUE.equals(b);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
