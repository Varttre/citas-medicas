package pe.consultorio.citas.entity;

/**
Cómo se protegen los bloques largos al ofrecer horarios. En el MVP se usa NUNCA. Sus valores son los
mismos que acepta la base de datos.
*/
public enum PoliticaProteccion {
    NUNCA, BLANDA, DURA
}
