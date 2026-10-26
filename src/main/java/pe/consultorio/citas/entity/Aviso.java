package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Contenido de la sección Avisos del paciente. Tabla aviso. */
@Entity
@Table(name = "aviso")
public class Aviso {

    /** Identificador del aviso. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aviso", updatable = false)
    private Long idAviso;

    /** Imagen de cabecera de la tarjeta del aviso. */
    @Column(name = "banner_url", nullable = false, length = 255)
    private String bannerUrl;

    /** Título del aviso. */
    @Column(name = "titulo", nullable = false, length = 100)
    private String titulo;

    /** Límite de 300 caracteres del flujo. */
    @Column(name = "texto", nullable = false, length = 300)
    private String texto;

    /** Página que abre [Conocer más], en otra pestaña. */
    @Column(name = "enlace", nullable = false, length = 255)
    private String enlace;

    /** Si el aviso se muestra en la sección Avisos del paciente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    public Long getIdAviso() {
        return idAviso;
    }

    public void setIdAviso(Long idAviso) {
        this.idAviso = idAviso;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getEnlace() {
        return enlace;
    }

    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
