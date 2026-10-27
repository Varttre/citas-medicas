package pe.consultorio.citas.service.disponibilidad;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.BloqueoTemporal;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.repository.BloqueoTemporalRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.service.ConfiguracionService;

/** Retiene un horario unos minutos mientras la persona termina de llenar la reserva. */
@Service
public class BloqueoTemporalService {

    private final BloqueoTemporalRepository bloqueos;
    private final MedicoRepository medicos;
    private final CuentaRepository cuentas;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public BloqueoTemporalService(BloqueoTemporalRepository bloqueos, MedicoRepository medicos,
            CuentaRepository cuentas, ConfiguracionService configuracion, Clock reloj) {
        this.bloqueos = bloqueos;
        this.medicos = medicos;
        this.cuentas = cuentas;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /**
    Retiene {@code [inicio, fin)} del médico para la cuenta; sustituye la retención anterior de esa cuenta.
    @throws HorarioOcupadoException si otra cuenta ya retiene un horario que se cruza
    */
    @Transactional
    public BloqueoTemporal tomar(Long idCuenta, Long idMedico, LocalDateTime inicio, LocalDateTime fin) {
        LocalDateTime ahora = ahora();
        bloqueos.borrarVencidosYDeCuenta(ahora, idCuenta);
        if (!bloqueos.vigentesQueCruzan(idMedico, inicio, fin, ahora).isEmpty()) {
            throw new HorarioOcupadoException();
        }
        BloqueoTemporal b = new BloqueoTemporal();
        b.setMedico(medicos.getReferenceById(idMedico));
        b.setCuenta(cuentas.getReferenceById(idCuenta));
        b.setInicio(inicio);
        b.setFin(fin);
        b.setExpiraEn(ahora.plusMinutes(configuracion.parametros().getBloqueoTemporalMin()));
        return bloqueos.save(b);
    }

    /** La retención aún vigente de la cuenta, si tiene una. */
    @Transactional(readOnly = true)
    public Optional<BloqueoTemporal> vigenteDe(Long idCuenta) {
        return bloqueos.findFirstByCuentaIdCuentaAndExpiraEnAfter(idCuenta, ahora());
    }

    /** Segundos que le quedan a la retención (0 si ya venció). */
    public long segundosRestantes(BloqueoTemporal b) {
        return Math.max(0, Duration.between(ahora(), b.getExpiraEn()).getSeconds());
    }

    /** Suelta la retención de la cuenta, si tiene una. */
    @Transactional
    public void liberar(Long idCuenta) {
        bloqueos.borrarDeCuenta(idCuenta);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
