package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.DatosHojaCorte;

/**
 * La «Hoja de corte» de un modelo: una ficha por línea de color con
 * cantidad, la misma ficha que las órdenes de corte de producción y de SMS
 * ({@link OrdenCorteExcelBuilder}), en un solo Excel. La foto de la cabecera
 * va en todas las fichas.
 */
@Service
public class HojaCorteService {

    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final OrdenCorteExcelBuilder ordenes = new OrdenCorteExcelBuilder();

    /**
     * Lanza IllegalArgumentException si no hay ninguna línea con cantidad:
     * sin ella no hay nada que cortar.
     */
    public byte[] excel(DatosHojaCorte datos, Imagen foto) throws IOException {
        List<OrdenCorte> fichas = fichas(datos, foto);
        if (fichas.isEmpty()) {
            throw new IllegalArgumentException("La hoja no tiene ninguna línea con cantidad");
        }
        return ordenes.generar(fichas);
    }

    static List<OrdenCorte> fichas(DatosHojaCorte datos, Imagen foto) {
        String modelo = datos.modelo().isEmpty() ? datos.referencia() : datos.modelo();
        String referencia = datos.referencia().isEmpty() ? modelo : datos.referencia();
        List<Imagen> fotos = foto == null ? List.of() : List.of(foto);
        String cliente = datos.nombreCliente().isEmpty() ? datos.cliente() : datos.nombreCliente();
        List<OrdenCorte> fichas = new ArrayList<>();
        for (DatosHojaCorte.Linea linea : datos.lineasConCantidad()) {
            PielesArticulo pieles = new PielesArticulo(piel(linea.principal()), piel(linea.forro()),
                    linea.combinaciones().stream().map(HojaCorteService::piel).toList());
            fichas.add(new OrdenCorte(cliente, datos.temporada(), referencia, modelo, datos.nombreBolso(),
                    linea.color(), linea.cantidad(), pieles, fotos, false));
        }
        return fichas;
    }

    /** «AMI SS27 - ULL027 - Hoja de Corte.xlsx», con la clave del cliente, como las de SMS. */
    public String nombreFichero(DatosHojaCorte datos) {
        String modelo = datos.modelo().isEmpty() ? datos.referencia() : datos.modelo();
        String nombre = String.join(" - ", List.of(
                (datos.cliente() + " " + datos.temporada()).trim(), modelo, "Hoja de Corte").stream()
                .filter(parte -> !parte.isBlank()).toList()) + ".xlsx";
        return CARACTERES_INSEGUROS.matcher(nombre).replaceAll("_").replaceAll("\\s+", " ");
    }

    private static PielCorte piel(DatosHojaCorte.Piel piel) {
        return new PielCorte(piel.nombre(), piel.color());
    }
}
