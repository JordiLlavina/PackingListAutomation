package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.PaletData;

/**
 * Traduce el JSON del envío (EnvioInput, tal como sale de las imágenes)
 * al modelo de dominio: expande los rangos de cajas a una CajaData por
 * caja física y monta los PaletData de cada destinación.
 *
 * Valida sin bloquear: las inconsistencias (cantidadTotal que no cuadra,
 * números de caja duplicados) se devuelven como avisos para la pantalla
 * de revisión, nunca se corrigen en silencio.
 */
@Service
public class EnvioImportService {

    public EnvioImportado importar(EnvioInput envio) {
        EnvioImportado resultado = new EnvioImportado();

        // Dudas de lectura de la extracción por imágenes: el modelo tiene
        // prohibido callarse lo que no lee con seguridad, y este es su cauce
        // hacia la pantalla de revisión.
        if (envio.getAvisos() != null) {
            for (String aviso : envio.getAvisos()) {
                if (aviso != null && !aviso.isBlank()) {
                    resultado.getAvisos().add("Lectura de las hojas: " + aviso.trim());
                }
            }
        }

        for (EnvioInput.DestinoInput destinoInput : envio.getDestinos()) {
            DestinoData destino = new DestinoData();
            destino.setNombreDestino(destinoInput.getDestino());
            destino.setCajas(new ArrayList<>());

            for (EnvioInput.ReferenciaInput referencia : destinoInput.getReferencias()) {
                destino.getCajas().addAll(expandirCajas(destinoInput, referencia, resultado));
            }
            destino.getCajas().sort(Comparator.comparingInt(CajaData::getNumeroCaja));
            detectarDuplicados(destino, resultado);

            resultado.getDestinos().add(new EnvioImportado.DestinoImportado(
                    destino, mapearPalets(destinoInput)));
        }
        return resultado;
    }

    /** Expande las entradas de caja (sueltas o rangos) de una referencia. */
    private List<CajaData> expandirCajas(EnvioInput.DestinoInput destinoInput,
                                         EnvioInput.ReferenciaInput referencia,
                                         EnvioImportado resultado) {
        List<CajaData> cajas = new ArrayList<>();
        for (EnvioInput.CajaRangoInput entrada : referencia.getCajas()) {
            if (entrada.esRango()) {
                for (int numero = entrada.getCajaInicio(); numero <= entrada.getCajaFin(); numero++) {
                    cajas.add(crearCaja(referencia, numero, entrada.getUnidadesPorCaja(),
                            entrada.getPesoBruto()));
                }
            } else {
                cajas.add(crearCaja(referencia, entrada.getCaja(), entrada.getUnidades(),
                        entrada.getPesoBruto()));
            }
        }

        if (referencia.getCantidadTotal() != null) {
            int suma = cajas.stream().mapToInt(CajaData::getCantidad).sum();
            if (suma != referencia.getCantidadTotal()) {
                resultado.getAvisos().add(String.format(
                        "%s / %s %s: la suma de unidades de las cajas (%d) no cuadra con cantidadTotal (%d)",
                        destinoInput.getDestino(), referencia.getReferencia(), referencia.getColor(),
                        suma, referencia.getCantidadTotal()));
            }
        }
        return cajas;
    }

    private CajaData crearCaja(EnvioInput.ReferenciaInput referencia, int numero, Integer unidades,
                               Double pesoBruto) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setNumeroPedido(referencia.getPedido());
        caja.setReferencia(referencia.getReferencia());
        caja.setCodigoColor(referencia.getColor());
        caja.setTamanoCaja(normalizarMedida(referencia.getMedidaCaja()));
        caja.setCantidad(unidades != null ? unidades : 0);
        caja.setTalla(normalizarTalla(referencia.getTalla()));
        caja.setModelo(referencia.getModelo());
        caja.setLivraisonCode(referencia.getLivraisonCode());
        caja.setCanal(referencia.getCanal());
        // El peso bruto solo viene en el JSON cuando la imagen lo indica; si
        // falta (null) queda pendiente para el WeightInferenceService o la
        // revisión manual. El neto no viene nunca: lo deriva la inferencia
        // (bruto - tara) o el peso tecleado a mano.
        caja.setPesoBrutoKg(pesoBruto);
        return caja;
    }

    /**
     * En las hojas manuscritas la talla se escribe "t75" o "+75" (la t de
     * talla); si la extracción cuela ese prefijo, la búsqueda de EAN en el
     * excel de pedido de AMI (clave con TAILLE exacta) falla en silencio.
     * Solo se limpia el prefijo cuando lo que queda son dígitos: una talla
     * "U" o cualquier valor raro se respeta tal cual.
     */
    /**
     * Una medida en blanco es una medida que falta, y se guarda como null
     * para que lo sea en todo el resto del recorrido: el desplegable TAMAÑO
     * de la revisión ofrece la opción vacía cuando el valor es null, pero
     * con "" pintaría una opción propia rotulada "(sin tara)" y ya
     * seleccionada — una medida vacía disfrazada de medida elegida.
     */
    private static String normalizarMedida(String medidaCaja) {
        if (medidaCaja == null || medidaCaja.isBlank()) {
            return null;
        }
        return medidaCaja.trim();
    }

    private static String normalizarTalla(String talla) {
        if (talla == null) {
            return null;
        }
        String limpia = talla.trim();
        if (limpia.matches("[tT+]\\d+")) {
            return limpia.substring(1);
        }
        return limpia;
    }

    private List<PaletData> mapearPalets(EnvioInput.DestinoInput destinoInput) {
        List<PaletData> palets = new ArrayList<>();
        if (destinoInput.getPalets() == null) {
            return palets;
        }
        for (EnvioInput.PaletInput entrada : destinoInput.getPalets()) {
            PaletData palet = new PaletData();
            // En el JSON los palets van anidados dentro de su destinación.
            palet.setDestino(destinoInput.getDestino());
            palet.setNumeroPalet(entrada.getPalet());
            palet.setCajaInicio(entrada.getCajaInicio());
            palet.setCajaFin(entrada.getCajaFin());
            palet.setMedidas(entrada.getMedidas());
            palet.setTara(entrada.getTara());
            palets.add(palet);
        }
        return palets;
    }

    /**
     * Un mismo número de caja en varias entradas es legítimo cuando cambia
     * el contenido (caja mixta de dos colores, cinturones con varias tallas,
     * canales distintos de APC): solo se avisa si se repite la combinación
     * completa, que sí huele a error de lectura de la imagen.
     */
    private void detectarDuplicados(DestinoData destino, EnvioImportado resultado) {
        Set<String> vistos = new HashSet<>();
        for (CajaData caja : destino.getCajas()) {
            String clave = caja.getNumeroCaja() + "|" + caja.getReferencia() + "|"
                    + caja.getCodigoColor() + "|" + caja.getTalla() + "|" + caja.getCanal();
            if (!vistos.add(clave)) {
                resultado.getAvisos().add(String.format(
                        "%s: la caja %d aparece más de una vez con la misma referencia, color, talla y canal (%s %s)",
                        destino.getNombreDestino(), caja.getNumeroCaja(),
                        caja.getReferencia(), caja.getCodigoColor()));
            }
        }
    }
}
