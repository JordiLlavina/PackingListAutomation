package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.puntotres.packinglist.seguridad.Seccion;

/**
 * Lo que manda la tabla de usuarios: una fila por persona, con sus casillas.
 *
 * Una casilla que no viene marcada no llega en la petición, así que "no
 * viene" significa "desmarcada". Por eso cada fila manda su usuarioId aunque
 * no se le haya tocado nada.
 */
public class AdministracionForm {

    private List<FilaUsuario> filas = new ArrayList<>();

    public List<FilaUsuario> getFilas() {
        return filas;
    }

    public void setFilas(List<FilaUsuario> filas) {
        this.filas = filas;
    }

    public static class FilaUsuario {

        private Long usuarioId;
        private boolean administrador;
        private boolean activo;
        private Set<Seccion> secciones = EnumSet.noneOf(Seccion.class);

        public Long getUsuarioId() {
            return usuarioId;
        }

        public void setUsuarioId(Long usuarioId) {
            this.usuarioId = usuarioId;
        }

        public boolean isAdministrador() {
            return administrador;
        }

        public void setAdministrador(boolean administrador) {
            this.administrador = administrador;
        }

        public boolean isActivo() {
            return activo;
        }

        public void setActivo(boolean activo) {
            this.activo = activo;
        }

        public Set<Seccion> getSecciones() {
            return secciones;
        }

        public void setSecciones(Set<Seccion> secciones) {
            this.secciones = secciones == null ? EnumSet.noneOf(Seccion.class) : secciones;
        }
    }
}
