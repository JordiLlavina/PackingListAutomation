package com.puntotres.packinglist.persistence;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Memoria del programa entre envíos: qué cartón y cuántas unidades por caja
 * usa cada referencia de cada cliente. Es una fachada sobre el ARTÍCULO del
 * catálogo de modelos ({@link Articulo}): la misma interfaz de siempre para
 * el taller, y los datos donde la pantalla del catálogo los enseña y edita.
 *
 * Solo se guarda lo que un humano ha dado por bueno: una referencia con las
 * unidades por caja sin rellenar NO se memoriza, porque memorizar el valor
 * por defecto lo daría por bueno la próxima vez y el error se propagaría en
 * silencio de un envío al siguiente. Y aquí no se propaga nada a las demás
 * pieles del modelo: eso lo hace solo la pantalla del catálogo, donde quien
 * teclea ve a qué hermanos llega.
 */
@Service
public class MemoriaReferencias {

    /**
     * Lo que la memoria sabe de una referencia. {@code pesoNetoKg} es lo que
     * pesa la mercancía de una caja llena, sin el cartón, y puede ser null:
     * nadie ha pesado todavía una caja de esa referencia.
     */
    public record DatosCaja(String medidaCaja, int unidadesPorCaja, Double pesoNetoKg) {
    }

    private final CatalogoModelos catalogo;

    public MemoriaReferencias(CatalogoModelos catalogo) {
        this.catalogo = catalogo;
    }

    /**
     * Vacío si la referencia no está en el catálogo o está pero nadie ha
     * decidido todavía su cartón y sus unidades: para el taller las dos
     * cosas son "no se sabe".
     */
    public Optional<DatosCaja> buscar(String cliente, String referencia) {
        return catalogo.articulo(cliente, referencia)
                .filter(articulo -> !articulo.getMedidaCaja().isEmpty() && articulo.getUnidadesPorCaja() != null)
                .map(articulo -> new DatosCaja(articulo.getMedidaCaja(), articulo.getUnidadesPorCaja(),
                        articulo.getPesoNetoKg()));
    }

    /**
     * Guarda o corrige lo aprendido de una referencia. Las llamadas sin
     * unidades por caja se ignoran en silencio: son las referencias que el
     * usuario ha dejado pendientes, y no hay nada que recordar de ellas.
     */
    @Transactional
    public void recordar(String cliente, String referencia,
                         String medidaCaja, Integer unidadesPorCaja) {
        recordar(cliente, referencia, medidaCaja, unidadesPorCaja, null);
    }

    /**
     * Lo mismo, guardando además lo que pesa la mercancía de una caja llena.
     *
     * El peso es opcional y un null NO borra el que hubiera: pesar una caja
     * cuesta bajarla a la báscula, y perder ese dato porque el envío siguiente
     * se generó sin pesar sería tirar el trabajo de alguien. Un peso que no
     * sea positivo se descarta igual que un campo vacío. Si la referencia no
     * estaba en el catálogo, se da de alta con su modelo y su piel.
     */
    @Transactional
    public void recordar(String cliente, String referencia, String medidaCaja,
                         Integer unidadesPorCaja, Double pesoNetoKg) {
        if (cliente == null || referencia == null || medidaCaja == null
                || unidadesPorCaja == null || unidadesPorCaja <= 0) {
            return;
        }
        Articulo articulo = catalogo.asegurarArticulo(cliente, referencia);
        articulo.setMedidaCaja(medidaCaja);
        articulo.setUnidadesPorCaja(unidadesPorCaja);
        if (pesoNetoKg != null && pesoNetoKg > 0) {
            articulo.setPesoNetoKg(pesoNetoKg);
        }
    }
}
