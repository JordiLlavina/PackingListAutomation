package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

/**
 * Todas las líneas de una referencia, con el cartón y las unidades por caja
 * que comparten.
 *
 * Esos dos datos van a nivel de GRUPO y no de fila porque son propiedades de
 * la referencia, no del color: todos los colores de un bolso se empaquetan
 * igual. Ponerlos por fila obligaría a propagar cada cambio a las demás y a
 * explicar en pantalla por qué editar una fila cambia otras.
 *
 * {@code unidadesPorCaja} puede ser null: "no se sabe todavía", que es lo que
 * impide generar. Nunca se sustituye por un cero ni por un valor de relleno,
 * porque un relleno se acabaría dando por bueno.
 */
public class GrupoReferencia {

    private final String referencia;
    private String medidaCaja;
    private Integer unidadesPorCaja;
    private OrigenDato origen;
    private final List<FilaDigerida> filas = new ArrayList<>();

    public GrupoReferencia(String referencia, String medidaCaja,
                           Integer unidadesPorCaja, OrigenDato origen) {
        this.referencia = referencia;
        this.medidaCaja = medidaCaja;
        this.unidadesPorCaja = unidadesPorCaja;
        this.origen = origen;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getMedidaCaja() {
        return medidaCaja;
    }

    public Integer getUnidadesPorCaja() {
        return unidadesPorCaja;
    }

    public OrigenDato getOrigen() {
        return origen;
    }

    public List<FilaDigerida> getFilas() {
        return filas;
    }

    /** Falta el dato que impide generar: la fila se marca en rojo. */
    public boolean estaPendiente() {
        return unidadesPorCaja == null || unidadesPorCaja <= 0;
    }

    /**
     * Lo que teclea el usuario en la pantalla de ajuste. Pasa a contar como
     * dato del taller —alguien lo ha mirado— y deja de ser un valor por
     * defecto, que es lo que decide si se memoriza al generar.
     */
    public void corregir(String medidaCaja, Integer unidadesPorCaja) {
        if (medidaCaja != null && !medidaCaja.isBlank()) {
            this.medidaCaja = medidaCaja.trim();
        }
        if (unidadesPorCaja != null && unidadesPorCaja > 0) {
            this.unidadesPorCaja = unidadesPorCaja;
        }
        this.origen = OrigenDato.TALLER;
    }
}
