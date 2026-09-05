package com.puntotres.packinglist.config;

/**
 * Norma de una destinación para el packing generado desde el taller.
 *
 * {@code alturaMaxCm} nulo NO significa "sin límite": significa "la de mi
 * destinación padre". Es el caso de CHINE FRANCH, que tiene prioridad de
 * reparto propia pero se apila como el WHOLESALE del que cuelga.
 */
public class ReglaDestinoTaller {

    private Integer alturaMaxCm;
    private Integer prioridad;
    private TipoMezcla mezcla = TipoMezcla.LIBRE;

    public Integer getAlturaMaxCm() {
        return alturaMaxCm;
    }

    public void setAlturaMaxCm(Integer alturaMaxCm) {
        this.alturaMaxCm = alturaMaxCm;
    }

    public Integer getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(Integer prioridad) {
        this.prioridad = prioridad;
    }

    public TipoMezcla getMezcla() {
        return mezcla;
    }

    public void setMezcla(TipoMezcla mezcla) {
        this.mezcla = mezcla;
    }
}
