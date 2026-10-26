package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Control de un solo uso del token de recuperación. Tabla token_recuperacion. */
@Entity
@Table(name = "token_recuperacion")
public class TokenRecuperacion {

    /** Identificador único del token firmado. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "jti", nullable = false, length = 36)
    private String jti;

    /** Cuenta que pidió recuperar su contraseña. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta")
    private Cuenta cuenta;

    @Column(name = "aud", nullable = false, length = 40)
    private String aud;

    /** Cuándo se generó el enlace. */
    @Column(name = "emitido_en", nullable = false)
    private LocalDateTime emitidoEn;

    /** Cuándo caduca el enlace; después se ofrece pedir uno nuevo. */
    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** Marca el único uso permitido. */
    @Column(name = "consumido_en")
    private LocalDateTime consumidoEn;

    /** Se activa al cambiar contraseña o dar de baja la cuenta. */
    @Column(name = "revocado", nullable = false)
    private boolean revocado;

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public void setCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
    }

    public String getAud() {
        return aud;
    }

    public void setAud(String aud) {
        this.aud = aud;
    }

    public LocalDateTime getEmitidoEn() {
        return emitidoEn;
    }

    public void setEmitidoEn(LocalDateTime emitidoEn) {
        this.emitidoEn = emitidoEn;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(LocalDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    public LocalDateTime getConsumidoEn() {
        return consumidoEn;
    }

    public void setConsumidoEn(LocalDateTime consumidoEn) {
        this.consumidoEn = consumidoEn;
    }

    public boolean isRevocado() {
        return revocado;
    }

    public void setRevocado(boolean revocado) {
        this.revocado = revocado;
    }
}
