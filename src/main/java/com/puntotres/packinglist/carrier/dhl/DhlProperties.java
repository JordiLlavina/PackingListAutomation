package com.puntotres.packinglist.carrier.dhl;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bloque envios.dhl del yml. Las credenciales llegan de variables de entorno (ver README de la feature). */
@ConfigurationProperties(prefix = "envios.dhl")
public class DhlProperties {

    private String baseUrl = "https://express.api.dhl.com/mydhlapi/test";
    private String apiKey = "";
    private String apiSecret = "";
    private String shipperAccount = "";
    private String plantillaEtiqueta = "ECOM26_84_A4_001";
    private LocalTime horaRecogida = LocalTime.of(12, 0);
    private int timeoutSegundos = 30;
    private Map<String, String> zonasHorarias = new LinkedHashMap<>();

    /** Sin api-key, api-secret y cuenta no se puede hablar con DHL; la sección lo avisa en vez de fallar. */
    public boolean configurado() {
        return !apiKey.isBlank() && !apiSecret.isBlank() && !shipperAccount.isBlank();
    }

    public String getBaseUrl() { return baseUrl; } public void setBaseUrl(String v) { baseUrl = v; }
    public String getApiKey() { return apiKey; } public void setApiKey(String v) { apiKey = v == null ? "" : v; }
    public String getApiSecret() { return apiSecret; } public void setApiSecret(String v) { apiSecret = v == null ? "" : v; }
    public String getShipperAccount() { return shipperAccount; } public void setShipperAccount(String v) { shipperAccount = v == null ? "" : v; }
    public String getPlantillaEtiqueta() { return plantillaEtiqueta; } public void setPlantillaEtiqueta(String v) { plantillaEtiqueta = v; }
    public LocalTime getHoraRecogida() { return horaRecogida; } public void setHoraRecogida(LocalTime v) { horaRecogida = v; }
    public int getTimeoutSegundos() { return timeoutSegundos; } public void setTimeoutSegundos(int v) { timeoutSegundos = v; }
    public Map<String, String> getZonasHorarias() { return zonasHorarias; } public void setZonasHorarias(Map<String, String> v) { zonasHorarias = v; }
}
