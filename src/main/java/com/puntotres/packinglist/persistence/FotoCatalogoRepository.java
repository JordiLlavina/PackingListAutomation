package com.puntotres.packinglist.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a las fotos de los modelos. Los listados devuelven
 * {@link ResumenFoto} y no la entidad para no arrastrar los bytes de cada
 * foto cada vez que se pinta una tabla.
 */
public interface FotoCatalogoRepository extends JpaRepository<FotoCatalogo, Long> {

    @Query("""
            select new com.puntotres.packinglist.persistence.ResumenFoto(
                f.id, f.codigoModelo, f.nombreFichero, f.principal, f.orden)
            from FotoCatalogo f
            where f.cliente = :cliente and f.codigoModelo in :modelos
            order by f.codigoModelo, f.orden, f.id""")
    List<ResumenFoto> resumenes(@Param("cliente") String cliente, @Param("modelos") Collection<String> modelos);

    Optional<FotoCatalogo> findByClienteAndCodigoModeloAndNombreFichero(String cliente, String codigoModelo,
                                                                        String nombreFichero);

    List<FotoCatalogo> findByClienteAndCodigoModeloOrderByOrdenAscIdAsc(String cliente, String codigoModelo);

    long countByClienteAndCodigoModelo(String cliente, String codigoModelo);

    void deleteByClienteAndCodigoModelo(String cliente, String codigoModelo);
}
