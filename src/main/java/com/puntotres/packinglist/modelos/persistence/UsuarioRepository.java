package com.puntotres.packinglist.modelos.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los usuarios de la app de modelos. El login se busca sin mirar mayúsculas. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    Optional<Usuario> findFirstByRolAndActivoTrueOrderByIdAsc(Rol rol);
}
