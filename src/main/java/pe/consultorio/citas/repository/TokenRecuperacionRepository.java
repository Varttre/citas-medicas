package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.TokenRecuperacion;

/** Acceso a la tabla token_recuperacion. */
public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, String> {
}
