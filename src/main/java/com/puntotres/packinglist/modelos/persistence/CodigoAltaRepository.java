package com.puntotres.packinglist.modelos.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a los códigos de alta de dispositivo. El código no es único en la
 * tabla (seis cifras se repiten con el tiempo): lo único que no se repite es
 * un código VIGENTE, y eso lo garantiza {@code Dispositivos}.
 */
public interface CodigoAltaRepository extends JpaRepository<CodigoAlta, Long> {

    List<CodigoAlta> findByCodigoAndUsadoEnIsNull(String codigo);

    void deleteByUsuarioIdAndUsadoEnIsNull(Long usuarioId);
}
