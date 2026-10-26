package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Auditoria;
import pe.consultorio.citas.repository.AuditoriaRepository;
import pe.consultorio.citas.repository.CuentaRepository;

/**
Registra acciones sensibles en la tabla auditoria. Los registros nunca se editan ni se borran.

Cada registro se guarda en su propia transacción (REQUIRES_NEW): queda escrito aunque la operación que lo pidió
falle después. Así, por ejemplo, un cambio de horario rechazado deja constancia del intento.
*/
@Service
public class AuditoriaService {

    private final AuditoriaRepository auditorias;
    private final CuentaRepository cuentas;
    private final Clock reloj;

    public AuditoriaService(AuditoriaRepository auditorias, CuentaRepository cuentas, Clock reloj) {
        this.auditorias = auditorias;
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    /**
    @param idCuenta   cuenta que hizo la acción; null si no hay sesión (por ejemplo, un login fallido)
    @param accion     una constante de {@link AccionAuditoria}
    @param entidad    tabla o concepto afectado, en mayúsculas (CITA, CUENTA, PARAMETRO…)
    @param idRegistro identificador del registro afectado; puede ser null
    @param detalle    qué cambió, por ejemplo «valor anterior → valor nuevo»; puede ser null
    @param motivo     motivo dado por la persona; puede ser null
    */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long idCuenta, String accion, String entidad, String idRegistro, String detalle,
            String motivo) {
        Auditoria a = new Auditoria();
        a.setCuenta(idCuenta == null ? null : cuentas.getReferenceById(idCuenta));
        a.setAccion(accion);
        a.setEntidad(entidad);
        a.setIdRegistro(idRegistro);
        a.setDetalle(detalle);
        a.setMotivo(motivo);
        a.setFechaHora(LocalDateTime.now(reloj));
        auditorias.save(a);
    }
}
