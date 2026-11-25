package com.puntotres.packinglist.persistence;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Memoria del programa entre envíos: qué cartón y cuántas unidades por caja
 * usa cada referencia de cada cliente, y lo que pesa una unidad. Es una
 * fachada sobre el ARTÍCULO del catálogo de modelos ({@link Articulo}): los
 * datos viven donde la pantalla del catálogo los enseña y edita.
 *
 * <p><b>Solo se aprende de los artículos que ya están en el catálogo</b>
 * (decisión del usuario, noviembre de 2026): si la referencia no está —ni el
 * modelo, o el modelo sí pero no esa piel— no se guarda nada y no se da de
 * alta. El catálogo se llena desde la carga de modelos, donde alguien mira lo
 * que entra; un packing que diera de alta lo que le llega lo llenaría de
 * referencias parciales o mal escritas.
 *
 * <p>Solo se guarda lo que un humano ha dado por bueno: una referencia con las
 * unidades por caja sin rellenar NO se memoriza, porque memorizar el valor
 * por defecto lo daría por bueno la próxima vez y el error se propagaría en
 * silencio de un envío al siguiente. Y aquí no se propaga nada a las demás
 * pieles del modelo: eso lo hace solo la pantalla del catálogo, donde quien
 * teclea ve a qué hermanos llega.
 */
@Service
public class MemoriaReferencias {

    /** Cómo se empaqueta una referencia: su cartón y cuántas unidades caben. */
    public record DatosCaja(String medidaCaja, int unidadesPorCaja) {
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
                .map(articulo -> new DatosCaja(articulo.getMedidaCaja(), articulo.getUnidadesPorCaja()));
    }

    /**
     * Lo que pesa una unidad de la referencia, sin embalaje. Vacío si no está
     * en el catálogo o nadie la ha pesado todavía.
     */
    public Optional<Double> pesoNetoUnidad(String cliente, String referencia) {
        return catalogo.articulo(cliente, referencia).map(Articulo::getPesoNetoUnidadKg);
    }

    /**
     * Guarda o corrige el cartón y las unidades por caja de una referencia
     * que ya está en el catálogo. Las llamadas sin unidades se ignoran en
     * silencio: son las referencias que el usuario ha dejado pendientes, y no
     * hay nada que recordar de ellas. No toca el peso.
     */
    @Transactional
    public void recordarCaja(String cliente, String referencia,
                             String medidaCaja, Integer unidadesPorCaja) {
        if (medidaCaja == null || unidadesPorCaja == null || unidadesPorCaja <= 0) {
            return;
        }
        catalogo.articulo(cliente, referencia).ifPresent(articulo -> {
            articulo.setMedidaCaja(medidaCaja);
            articulo.setUnidadesPorCaja(unidadesPorCaja);
        });
    }

    /**
     * Guarda lo que pesa una unidad de una referencia que ya está en el
     * catálogo, a cuatro decimales (un cinturón pesa del orden de 0,15 kg y
     * con dos se perdería). Devuelve si se ha guardado.
     *
     * Un peso nulo o que no sea positivo NO borra el que hubiera: pesar una
     * caja cuesta bajarla a la báscula, y perder ese dato porque el envío
     * siguiente se generó sin pesar sería tirar el trabajo de alguien.
     */
    @Transactional
    public boolean recordarPesoUnidad(String cliente, String referencia, Double pesoNetoUnidadKg) {
        if (pesoNetoUnidadKg == null || !(pesoNetoUnidadKg > 0)) {
            return false;
        }
        Optional<Articulo> articulo = catalogo.articulo(cliente, referencia);
        articulo.ifPresent(encontrado -> encontrado.setPesoNetoUnidadKg(redondear4(pesoNetoUnidadKg)));
        return articulo.isPresent();
    }

    private static double redondear4(double valor) {
        return Math.round(valor * 10_000.0) / 10_000.0;
    }
}
