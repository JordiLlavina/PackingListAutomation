package com.puntotres.packinglist.modelos.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.puntotres.packinglist.modelos.persistence.Dispositivo;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Lo que solo hace el admin: dar de alta y cambiar usuarios y revocar los
 * móviles y tablets del taller (que se dan de alta solos al entrar). Ninguna respuesta lleva el hash de la contraseña ni el del
 * token de un dispositivo.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final Usuarios usuarios;
    private final Dispositivos dispositivos;

    public AdminController(Usuarios usuarios, Dispositivos dispositivos) {
        this.usuarios = usuarios;
        this.dispositivos = dispositivos;
    }

    /** Un usuario tal como lo ve la pantalla de administración. */
    /**
     * Un usuario tal como lo ve la pantalla de administración. {@code conPassword}
     * dice si puede entrar: los del taller dados de alta cuando entraban con
     * un código no tienen contraseña hasta que se la pone el admin.
     */
    public record UsuarioDto(Long id, String nombre, String login, Rol rol, boolean activo,
                             LocalDateTime ultimoAcceso, boolean conPassword) {

        static UsuarioDto de(Usuario usuario) {
            return new UsuarioDto(usuario.getId(), usuario.getNombre(), usuario.getLogin(), usuario.getRol(),
                    usuario.isActivo(), usuario.getUltimoAcceso(), usuario.getPasswordHash() != null);
        }
    }

    /** Un usuario nuevo, con su contraseña. */
    public record NuevoUsuario(String nombre, String login, String password, Rol rol) {
    }

    /** Lo que se cambia de un usuario: lo que no llega, no se toca. */
    public record CambiosUsuario(String nombre, Rol rol, Boolean activo, String password) {
    }

    /** Un dispositivo dado de alta, con el nombre de su usuario. */
    public record DispositivoDto(Long id, Long usuarioId, String usuario, String nombre, LocalDateTime alta,
                                 LocalDateTime ultimoUso, boolean revocado) {
    }

    @GetMapping("/usuarios")
    public List<UsuarioDto> usuarios() {
        return usuarios.todos().stream().map(UsuarioDto::de).toList();
    }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioDto> crearUsuario(@RequestBody NuevoUsuario nuevo) {
        Usuario usuario = usuarios.crear(new Usuarios.Alta(nuevo.nombre(), nuevo.login(), nuevo.password(),
                nuevo.rol()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioDto.de(usuario));
    }

    @PatchMapping("/usuarios/{id}")
    public UsuarioDto cambiarUsuario(@PathVariable Long id, @RequestBody CambiosUsuario cambios,
                                     @AuthenticationPrincipal UsuarioActual quien) {
        usuarioQueExiste(id);
        usuarios.cambiar(id, cambios.nombre(), cambios.rol(), cambios.activo(), cambios.password(), quien);
        return UsuarioDto.de(usuarioQueExiste(id));
    }

    @GetMapping("/dispositivos")
    public List<DispositivoDto> dispositivos() {
        Map<Long, String> nombres = usuarios.todos().stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre, (a, b) -> a));
        return dispositivos.todos().stream()
                .map(dispositivo -> dispositivoDto(dispositivo, nombres))
                .toList();
    }

    @PostMapping("/dispositivos/{id}/revocar")
    public ResponseEntity<Void> revocar(@PathVariable Long id) {
        if (dispositivos.todos().stream().noneMatch(dispositivo -> dispositivo.getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún dispositivo con id " + id);
        }
        dispositivos.revocar(id);
        return ResponseEntity.noContent().build();
    }

    private Usuario usuarioQueExiste(Long id) {
        return usuarios.porId(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún usuario con id " + id));
    }

    private static DispositivoDto dispositivoDto(Dispositivo dispositivo, Map<Long, String> nombres) {
        return new DispositivoDto(dispositivo.getId(), dispositivo.getUsuarioId(),
                nombres.get(dispositivo.getUsuarioId()), dispositivo.getNombre(), dispositivo.getAlta(),
                dispositivo.getUltimoUso(), dispositivo.revocado());
    }
}
