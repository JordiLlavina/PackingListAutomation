package com.puntotres.packinglist.seguridad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByOidEntra(String oidEntra);

    List<Usuario> findAllByOrderByNombreAsc();
}
