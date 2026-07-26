package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaCajaApc;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaPaletApc;

/**
 * Etiquetas de caja y palet de APC: cuatro destinaciones con plantilla
 * propia (JAPAN, KOREA, USA, WH CROSSLOG); ver ApcEtiquetaLayout. No pide
 * archivos al usuario: los datos estáticos van horneados en cada plantilla
 * y el Order N° y el Livraison code salen como "NOT FOUND" hasta que se
 * implemente su búsqueda (iteración futura).
 *
 * Una caja física = un numeroCaja; los cinturones (línea con talla, APC no
 * usa el prefijo UBL) agrupan unidades por talla en SIZE/PIECES. El peso es
 * el de la línea líder de cada caja ({@link CajaFisica}) y el del palet, la
 * suma de los de sus cajas más la tara (10 kg si el JSON no la trae).
 */
@Service
public class ApcEtiquetasGenerador implements GeneradorEtiquetasCliente {

    private static final String NO_DISPONIBLE = "NOT FOUND";
    private static final double TARA_PALET_KG_DEFECTO = 10.0;
    private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

    private final ApcEtiquetasExcelBuilder builder;

    public ApcEtiquetasGenerador(ApcEtiquetasExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "APC";
    }

    @Override
    public boolean soportaDestino(String nombreDestino) {
        return ApcEtiquetaLayout.paraDestino(nombreDestino).isPresent();
    }

    @Override
    public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
        return List.of();
    }

    @Override
    public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                                      DatosEnvio envio, Map<String, byte[]> archivos)
            throws IOException {
        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        for (EnvioImportado.DestinoImportado importado : destinos) {
            DestinoData destino = importado.getDestino();
            Optional<ApcEtiquetaLayout> layout =
                    ApcEtiquetaLayout.paraDestino(destino.getNombreDestino());
            if (layout.isEmpty()) {
                resultado.getAvisos().add("Destinación '" + destino.getNombreDestino()
                        + "' sin etiquetas de APC implementadas: se omite");
                continue;
            }
            resultado.getExcels().add(generarDestino(destino, importado.getPalets(),
                    layout.get(), envio, resultado.getAvisos()));
        }
        return resultado;
    }

    private ExcelGenerado generarDestino(DestinoData destino, List<PaletData> palets,
                                         ApcEtiquetaLayout layout, DatosEnvio envio,
                                         List<String> avisos) throws IOException {
        // Una caja física por numeroCaja, en orden ascendente.
        List<CajaFisica> cajasFisicas = CajaFisica.agrupar(destino.getCajas().stream()
                .sorted(Comparator.comparingInt(CajaData::getNumeroCaja))
                .toList());

        List<EtiquetaCajaApc> etiquetas = new ArrayList<>();
        List<CajaData> cajasPendientes = new ArrayList<>();
        int posicion = 0;
        int total = cajasFisicas.size();
        for (CajaFisica caja : cajasFisicas) {
            posicion++;
            etiquetas.add(etiquetaDe(caja, posicion, total,
                    destino.getNombreDestino(), avisos, cajasPendientes));
        }

        List<EtiquetaPaletApc> etiquetasPalet =
                etiquetasDePalet(cajasFisicas, destino.getNombreDestino(), palets, avisos);

        if (destino.getCajas().stream().anyMatch(caja -> caja.getNumeroPalet() == null)) {
            avisos.add("Destinación " + destino.getNombreDestino()
                    + ": hay cajas sin palet asignado, no salen en ninguna etiqueta de palet");
        }

        String nombreFichero = ("Etiquetas_APC_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas, etiquetasPalet);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    private EtiquetaCajaApc etiquetaDe(CajaFisica caja, int posicion, int total,
                                       String nombreDestino, List<String> avisos,
                                       List<CajaData> cajasPendientes) {
        List<CajaData> lineas = caja.lineas();
        CajaData lider = caja.lider();

        // Caja mixta de verdad (varias referencias o colores): se etiqueta
        // con la primera y se avisa, igual que en AMI.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(claveRefColor(linea));
        }
        if (refsColores.size() > 1) {
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " mezcla varias referencias/colores: la etiqueta lleva "
                    + lider.getReferencia() + " " + lider.getCodigoColor());
        }
        List<CajaData> propias = lineas.stream()
                .filter(linea -> claveRefColor(lider).equals(claveRefColor(linea)))
                .toList();

        // Cinturón = línea con talla (APC no usa el prefijo UBL). Las
        // unidades se agrupan por talla, tallas en orden numérico.
        Map<String, Integer> unidadesPorTalla = new LinkedHashMap<>();
        propias.stream()
                .filter(linea -> linea.getTalla() != null)
                .sorted(Comparator.comparingInt(ApcEtiquetasGenerador::tallaNumerica))
                .forEach(linea -> unidadesPorTalla.merge(
                        linea.getTalla(), linea.getCantidad(), Integer::sum));

        String size;
        String piezas;
        if (unidadesPorTalla.isEmpty()) {
            size = "U";
            piezas = String.valueOf(propias.stream().mapToInt(CajaData::getCantidad).sum());
        } else if (unidadesPorTalla.size() == 1) {
            var unica = unidadesPorTalla.entrySet().iterator().next();
            size = unica.getKey();
            piezas = String.valueOf(unica.getValue());
        } else {
            size = String.join("-", unidadesPorTalla.keySet());
            piezas = String.join(",", unidadesPorTalla.entrySet().stream()
                    .map(e -> e.getValue() + "-" + e.getKey()).toList());
        }

        // El peso es de la caja física ENTERA y viene una sola vez, en su
        // línea líder; las demás líneas no aportan peso.
        Double peso = caja.pesoBrutoKg();
        if (peso == null) {
            cajasPendientes.add(lider);
        }
        return new EtiquetaCajaApc(NO_DISPONIBLE, NO_DISPONIBLE, lider.getReferencia(),
                lider.getCodigoColor(), size, piezas, posicion + " / " + total, kg(peso));
    }

    /**
     * Peso del palet: la suma de los pesos de sus cajas FÍSICAS (uno por
     * caja, el de su línea líder) más la tara. En blanco, con aviso, si
     * alguna de esas cajas no trae peso.
     */
    private List<EtiquetaPaletApc> etiquetasDePalet(List<CajaFisica> cajasFisicas,
                                                    String nombreDestino,
                                                    List<PaletData> palets,
                                                    List<String> avisos) {
        if (palets.isEmpty()) {
            avisos.add("Destinación " + nombreDestino
                    + " sin palets: la hoja de etiquetas de palet sale en blanco");
            return List.of();
        }
        List<EtiquetaPaletApc> etiquetas = new ArrayList<>();
        for (PaletData palet : palets.stream()
                .sorted(Comparator.comparingInt(PaletData::getNumeroPalet)).toList()) {
            int numeroCajas = palet.getCajaFin() - palet.getCajaInicio() + 1;
            Double peso = null;
            boolean completo = true;
            for (CajaFisica caja : cajasFisicas) {
                if (!Integer.valueOf(palet.getNumeroPalet()).equals(caja.numeroPalet())) {
                    continue;
                }
                if (caja.pesoBrutoKg() == null) {
                    completo = false;
                } else {
                    peso = (peso == null ? 0 : peso) + caja.pesoBrutoKg();
                }
            }
            if (!completo || peso == null) {
                avisos.add("Palet " + palet.getNumeroPalet() + " de " + nombreDestino
                        + " con cajas sin peso: etiqueta de palet sin peso");
                peso = null;
            } else {
                peso += palet.getTara() != null ? palet.getTara() : TARA_PALET_KG_DEFECTO;
            }
            etiquetas.add(new EtiquetaPaletApc(numeroCajas, kg(peso)));
        }
        return etiquetas;
    }

    private static String kg(Double peso) {
        return peso == null ? null : String.format(ESPANOL, "%.2f Kg", peso);
    }

    private static String claveRefColor(CajaData caja) {
        return caja.getReferencia() + "|" + caja.getCodigoColor();
    }

    private static int tallaNumerica(CajaData caja) {
        try {
            return Integer.parseInt(caja.getTalla().trim());
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE; // tallas raras al final, sin romper
        }
    }
}
