package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.config.TaraProperties;

/**
 * El catálogo de taras que manda en la aplicación arrancada: la tabla de la
 * base de datos, editable desde /taras sin tocar ficheros ni recompilar.
 *
 * La primera vez que arranca en una máquina la tabla está vacía y se siembra
 * con el bloque {@code packing-list.taras} del yml. La siembra solo ocurre si
 * está vacía: si no, un arranque machacaría lo que se hubiera pesado desde la
 * pantalla, que es justo el dato bueno.
 */
@Service
@Primary
public class CatalogoTarasJpa implements CatalogoTaras {

    private final TaraCajaRepository repositorio;
    private final TaraProperties semilla;

    public CatalogoTarasJpa(TaraCajaRepository repositorio, TaraProperties semilla) {
        this.repositorio = repositorio;
        this.semilla = semilla;
    }

    @Override
    public Optional<Double> taraPara(String tamanoCaja) {
        if (tamanoCaja == null) {
            return Optional.empty();
        }
        return repositorio.findById(CatalogoTaras.normalizar(tamanoCaja)).map(TaraCaja::getTaraKg);
    }

    @Override
    public List<String> tamanosDeMayorAMenor() {
        return CatalogoTaras.deMayorAMenor(
                repositorio.findAll().stream().map(TaraCaja::getMedida).toList());
    }

    /** Las taras completas, de la caja más grande a la más pequeña, para /taras. */
    public List<TaraCaja> todas() {
        Map<String, TaraCaja> porMedida = repositorio.findAll().stream()
                .collect(Collectors.toMap(TaraCaja::getMedida, tara -> tara));
        return CatalogoTaras.deMayorAMenor(porMedida.keySet()).stream()
                .map(porMedida::get)
                .toList();
    }

    /** Alta o corrección de una tara. La medida se normaliza antes de guardar. */
    @Transactional
    public void guardar(String medida, double taraKg) {
        String clave = CatalogoTaras.normalizar(medida);
        repositorio.findById(clave).ifPresentOrElse(
                tara -> tara.actualizar(taraKg),
                () -> repositorio.save(new TaraCaja(clave, taraKg)));
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sembrarSiEstaVacia() {
        if (repositorio.count() > 0) {
            return;
        }
        semilla.getTaras().forEach((medida, tara) ->
                repositorio.save(new TaraCaja(CatalogoTaras.normalizar(medida), tara)));
    }
}
