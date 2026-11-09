package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los usuarios de la app de modelos. El login se busca sin mirar mayúsculas. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    Optional<Usuario> findFirstByRolAndActivoTrueOrderByIdAsc(Rol rol);

    /** Los activos con alguno de esos roles: a quién se le manda un push. */
    List<Usuario> findByRolInAndActivoTrue(Collection<Rol> roles);
}
