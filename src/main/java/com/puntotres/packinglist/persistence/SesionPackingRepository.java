package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las sesiones de packing guardadas. */
public interface SesionPackingRepository extends JpaRepository<SesionPacking, Long> {

    /**
     * La lista de «Cargar Sesión», la más reciente primero, por una
     * proyección: ni el envío ni el excel de pedido hacen falta para listar.
     */
    @Query("select new com.puntotres.packinglist.persistence.ResumenSesionPacking(s.id, s.cliente, "
            + "s.creada, s.temporada, s.facturas, s.destinos) "
            + "from SesionPacking s order by s.creada desc, s.id desc")
    List<ResumenSesionPacking> resumenes();
}
