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
    /**
     * Peso bruto (kg) de una caja LLENA de esta referencia, o null si no se ha
     * pesado. Es opcional: sin él los pesos se quedan en blanco y se rellenan
     * en la revisión, como en cualquier otra vía de entrada.
     */
    private Double pesoBrutoKg;
    /**
     * El peso de arriba sale de la memoria (el catálogo) y no de la hoja del
     * taller ni de lo tecleado en el ajuste. Se enseña igual, pero no se
     * escribe en las cajas: lo pone la revisión desde el catálogo, marcado
     * en gris. Escrito en las cajas parecería pesado en este envío y se
     * volvería a guardar como si alguien lo hubiera pesado.
     */
    private boolean pesoDeMemoria;
    private OrigenDato origen;
    private final List<FilaDigerida> filas = new ArrayList<>();

    public GrupoReferencia(String referencia, String medidaCaja,
                           Integer unidadesPorCaja, OrigenDato origen) {
        this(referencia, medidaCaja, unidadesPorCaja, null, origen);
    }

    public GrupoReferencia(String referencia, String medidaCaja, Integer unidadesPorCaja,
                           Double pesoBrutoKg, OrigenDato origen) {
        this(referencia, medidaCaja, unidadesPorCaja, pesoBrutoKg, false, origen);
    }

    public GrupoReferencia(String referencia, String medidaCaja, Integer unidadesPorCaja,
                           Double pesoBrutoKg, boolean pesoDeMemoria, OrigenDato origen) {
        this.referencia = referencia;
        this.medidaCaja = medidaCaja;
        this.unidadesPorCaja = unidadesPorCaja;
        this.pesoBrutoKg = pesoBrutoKg;
        this.pesoDeMemoria = pesoBrutoKg != null && pesoDeMemoria;
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

    public Double getPesoBrutoKg() {
        return pesoBrutoKg;
    }

    /**
     * El peso que se escribe en las cajas: el de la hoja del taller o el
     * tecleado en el ajuste. El de la memoria no (ver {@code pesoDeMemoria}).
     */
    public Double getPesoParaLasCajas() {
        return pesoDeMemoria ? null : pesoBrutoKg;
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
        corregir(medidaCaja, unidadesPorCaja, null);
    }

    /**
     * Lo mismo, con el peso bruto de una caja llena. Un peso de cero o
     * negativo se ignora igual que un campo vacío: es lo que llega de una
     * casilla que nadie ha rellenado, y darlo por bueno pondría un cero en el
     * packing list que lee el cliente.
     *
     * El formulario devuelve siempre el peso que enseñaba: si es el mismo que
     * vino de la memoria, sigue siendo de la memoria. Solo un número distinto
     * es un peso que alguien ha tecleado.
     */
    public void corregir(String medidaCaja, Integer unidadesPorCaja, Double pesoBrutoKg) {
        if (medidaCaja != null && !medidaCaja.isBlank()) {
            this.medidaCaja = medidaCaja.trim();
        }
        if (unidadesPorCaja != null && unidadesPorCaja > 0) {
            this.unidadesPorCaja = unidadesPorCaja;
        }
        if (pesoBrutoKg != null && pesoBrutoKg > 0 && !mismoPeso(pesoBrutoKg, this.pesoBrutoKg)) {
            this.pesoBrutoKg = pesoBrutoKg;
            this.pesoDeMemoria = false;
        }
        this.origen = OrigenDato.TALLER;
    }

    /** A los dos decimales que enseña la pantalla. */
    private static boolean mismoPeso(double tecleado, Double actual) {
        return actual != null && Math.round(tecleado * 100) == Math.round(actual * 100);
    }
}
