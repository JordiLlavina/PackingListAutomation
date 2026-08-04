package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Deja las destinaciones de un envío listas para generar: resuelve cada una
 * a su destino padre del catálogo del cliente, fusiona en uno solo las que
 * comparten padre y les estampa su Livraison code.
 *
 * Vive fuera de {@link EnvioImportService} a propósito: el importador traduce
 * el JSON a dominio y no conoce el catálogo de clientes. Se llama desde el
 * controlador ANTES de asignar palets, para que la asignación y la inferencia
 * trabajen ya sobre las destinaciones definitivas.
 *
 * <b>Nunca renumera cajas ni palets.</b> Si dos hijas del mismo padre traen
 * el mismo número de caja, se avisa y se dejan como están: el bulto lleva ese
 * número pegado físicamente, y el packing list tiene que coincidir con la
 * etiqueta, no al revés. Se corrige en la revisión, donde caja y palet son
 * editables.
 *
 * Un cliente sin catálogo de destinos (AMI, genéricos) pasa intacto.
 */
@Service
public class ResolutorDestinosPadre {

    public ResultadoDestinos resolver(List<EnvioImportado.DestinoImportado> destinos,
                                      ClienteConfig cliente, String fechaEnvio) {
        ResultadoDestinos resultado = new ResultadoDestinos();
        if (cliente == null || cliente.getDestinos().isEmpty()) {
            resultado.getDestinos().addAll(destinos);
            return resultado;
        }

        // Clave = nombre del padre; valor = las hijas que van bajo él, en el
        // orden en que llegaron. Los destinos sin padre conocido conservan su
        // sitio con su propio nombre como clave.
        Map<String, List<EnvioImportado.DestinoImportado>> porPadre = new LinkedHashMap<>();
        Map<String, ClienteConfig.DestinoResuelto> configPorPadre = new LinkedHashMap<>();

        for (EnvioImportado.DestinoImportado importado : destinos) {
            String nombreHija = importado.getDestino().getNombreDestino();
            Optional<ClienteConfig.DestinoResuelto> resuelto = cliente.destinoPadrePara(nombreHija);
            String clave = resuelto.map(ClienteConfig.DestinoResuelto::nombrePadre).orElse(nombreHija);
            resuelto.ifPresent(valor -> configPorPadre.put(clave, valor));
            // El canal (columna DESTINATION) es lo único donde sobrevive la
            // hija. Si el JSON ya trae uno, manda el JSON.
            if (resuelto.isPresent()) {
                for (CajaData caja : importado.getDestino().getCajas()) {
                    if (caja.getCanal() == null || caja.getCanal().isBlank()) {
                        caja.setCanal(nombreHija.trim().toUpperCase());
                    }
                }
            }
            porPadre.computeIfAbsent(clave, k -> new ArrayList<>()).add(importado);
        }

        porPadre.forEach((nombrePadre, hijas) -> {
            EnvioImportado.DestinoImportado fusionado =
                    fusionar(nombrePadre, hijas, resultado.getAvisos());
            ClienteConfig.DestinoResuelto config = configPorPadre.get(nombrePadre);
            if (config != null) {
                String codigo = LivraisonCode.generar(nombrePadre, config.config(),
                        fechaEnvio, resultado.getAvisos());
                fusionado.getDestino().getCajas().forEach(caja -> caja.setLivraisonCode(codigo));
            }
            resultado.getDestinos().add(fusionado);
        });
        return resultado;
    }

    /**
     * Una sola destinación con las cajas y los palets de todas sus hijas, en
     * el orden en que llegaron. Con una única hija se reutiliza su objeto:
     * no hay nada que fusionar y así se conservan sus listas tal cual.
     */
    private EnvioImportado.DestinoImportado fusionar(
            String nombrePadre, List<EnvioImportado.DestinoImportado> hijas, List<String> avisos) {
        if (hijas.size() == 1) {
            hijas.get(0).getDestino().setNombreDestino(nombrePadre);
            hijas.get(0).getPalets().forEach(palet -> palet.setDestino(nombrePadre));
            return hijas.get(0);
        }

        // Antes de copiar nada: los avisos citan a las hijas por su nombre
        // original, que se pierde en cuanto el destino nuevo toma el del padre.
        avisarDeNumerosRepetidos(nombrePadre, hijas, avisos);

        List<CajaData> cajas = new ArrayList<>();
        List<PaletData> palets = new ArrayList<>();
        for (EnvioImportado.DestinoImportado hija : hijas) {
            cajas.addAll(hija.getDestino().getCajas());
            hija.getPalets().forEach(palet -> palet.setDestino(nombrePadre));
            palets.addAll(hija.getPalets());
        }
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombrePadre);
        destino.setCajas(cajas);
        return new EnvioImportado.DestinoImportado(destino, palets);
    }

    private void avisarDeNumerosRepetidos(String nombrePadre,
                                          List<EnvioImportado.DestinoImportado> hijas,
                                          List<String> avisos) {
        avisarDe("la caja", nombrePadre, hijas, avisos, hija -> {
            Set<Integer> numeros = new TreeSet<>();
            hija.getDestino().getCajas().forEach(caja -> numeros.add(caja.getNumeroCaja()));
            return numeros;
        });
        avisarDe("el palet", nombrePadre, hijas, avisos, hija -> {
            Set<Integer> numeros = new TreeSet<>();
            hija.getPalets().forEach(palet -> numeros.add(palet.getNumeroPalet()));
            return numeros;
        });
    }

    /** queEs incluye su artículo ("la caja", "el palet") para que el aviso concuerde. */
    private void avisarDe(String queEs, String nombrePadre,
                          List<EnvioImportado.DestinoImportado> hijas, List<String> avisos,
                          Function<EnvioImportado.DestinoImportado, Set<Integer>> numerosDe) {
        Map<Integer, Set<String>> hijasPorNumero = new LinkedHashMap<>();
        for (EnvioImportado.DestinoImportado hija : hijas) {
            for (Integer numero : numerosDe.apply(hija)) {
                hijasPorNumero.computeIfAbsent(numero, n -> new LinkedHashSet<>())
                        .add(hija.getDestino().getNombreDestino());
            }
        }
        hijasPorNumero.forEach((numero, nombres) -> {
            if (nombres.size() > 1) {
                avisos.add(nombrePadre + ": " + queEs + " " + numero + " llega de "
                        + String.join(" y de ", nombres)
                        + "; se fusionan sin renumerar, revísalo en la tabla");
            }
        });
    }
}
