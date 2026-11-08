package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Acceso a los dispositivos dados de alta, que se buscan por el hash de su token. */
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByTokenHash(String tokenHash);

    /**
     * Apunta el último uso tocando SOLO esa columna, y solo si el dispositivo
     * sigue sin revocar. Guardar la entidad entera no vale: la carga el filtro
     * de la cookie fuera de toda transacción y, si el admin lo revoca entre
     * medias, guardarla volvería a escribir {@code revocado_en} vacío y
     * desharía la revocación sin que nadie lo viera.
     *
     * @return las filas tocadas: 0 si no existe o ya está revocado
     */
    @Modifying
    @Transactional
    @Query("update Dispositivo d set d.ultimoUso = :ahora where d.id = :id and d.revocadoEn is null")
    int apuntarUso(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);
}
