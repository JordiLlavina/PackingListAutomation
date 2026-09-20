package com.puntotres.packinglist.seguridad;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * El primer administrador no puede salir de la propia aplicación: no hay
 * nadie que se lo conceda. De ahí esta lista.
 */
@Component
@ConfigurationProperties(prefix = "seguridad")
public class SeguridadProperties {

    private List<String> administradoresIniciales = List.of();

    public List<String> getAdministradoresIniciales() {
        return administradoresIniciales;
    }

    public void setAdministradoresIniciales(List<String> administradoresIniciales) {
        this.administradoresIniciales = administradoresIniciales;
    }

    public boolean esAdministradorInicial(String correo) {
        return correo != null && administradoresIniciales.stream()
                .anyMatch(c -> c.trim().toLowerCase(Locale.ROOT)
                        .equals(correo.trim().toLowerCase(Locale.ROOT)));
    }
}
