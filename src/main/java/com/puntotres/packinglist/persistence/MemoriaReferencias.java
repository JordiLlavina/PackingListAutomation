package com.puntotres.packinglist.persistence;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memoria del programa entre envíos: qué cartón y cuántas unidades por caja
 * usa cada referencia de cada cliente.
 *
 * Solo se guarda lo que un humano ha dado por bueno: una referencia con las
 * unidades por caja sin rellenar NO se memoriza, porque memorizar el valor
 * por defecto lo daría por bueno la próxima vez y el error se propagaría en
 * silencio de un envío al siguiente.
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

    private final MemoriaCajaReferenciaRepository repositorio;

    public MemoriaReferencias(MemoriaCajaReferenciaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Optional<DatosCaja> buscar(String cliente, String referencia) {
        if (cliente == null || referencia == null) {
            return Optional.empty();
        }
        return repositorio.findById(clave(cliente, referencia))
                .map(fila -> new DatosCaja(fila.getMedidaCaja(), fila.getUnidadesPorCaja(),
                        fila.getPesoNetoKg()));
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
     * sea positivo se descarta igual que un campo vacío.
     */
    @Transactional
    public void recordar(String cliente, String referencia, String medidaCaja,
                         Integer unidadesPorCaja, Double pesoNetoKg) {
        if (cliente == null || referencia == null || medidaCaja == null
                || unidadesPorCaja == null || unidadesPorCaja <= 0) {
            return;
        }
        Double peso = pesoNetoKg != null && pesoNetoKg > 0 ? pesoNetoKg : null;
        MemoriaCajaReferenciaId clave = clave(cliente, referencia);
        repositorio.findById(clave).ifPresentOrElse(
                fila -> fila.actualizar(medidaCaja, unidadesPorCaja, peso),
                () -> repositorio.save(new MemoriaCajaReferencia(
                        clave.cliente(), clave.referencia(), medidaCaja, unidadesPorCaja, peso)));
    }

    /**
     * Cliente y referencia en mayúsculas y sin espacios de sobra: la
     * referencia llega tecleada a mano o leída de un excel, y "ull164" y
     * "ULL164 " son la misma.
     */
    private static MemoriaCajaReferenciaId clave(String cliente, String referencia) {
        return new MemoriaCajaReferenciaId(
                cliente.trim().toUpperCase(Locale.ROOT),
                referencia.trim().toUpperCase(Locale.ROOT));
    }
}
