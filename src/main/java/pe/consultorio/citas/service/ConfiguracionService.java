package pe.consultorio.citas.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.ParametroConsultorio;
import pe.consultorio.citas.entity.PlazoTransicion;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.Transicion;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.ParametroConsultorioRepository;
import pe.consultorio.citas.repository.PlazoTransicionRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/**
Lectura de la configuración del consultorio: la fila única de parámetros, los plazos de cada transición y los
catálogos activos. La escritura (pantalla del administrador) se añade aquí mismo.
*/
@Service
@Transactional(readOnly = true)
public class ConfiguracionService {

    private static final short ID_PARAMETROS = 1;

    private final ParametroConsultorioRepository parametros;
    private final PlazoTransicionRepository plazos;
    private final TipoConsultaRepository tipos;
    private final EspecialidadRepository especialidades;

    public ConfiguracionService(ParametroConsultorioRepository parametros, PlazoTransicionRepository plazos,
            TipoConsultaRepository tipos, EspecialidadRepository especialidades) {
        this.parametros = parametros;
        this.plazos = plazos;
        this.tipos = tipos;
        this.especialidades = especialidades;
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
}
