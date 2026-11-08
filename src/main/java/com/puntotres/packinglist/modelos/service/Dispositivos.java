package com.puntotres.packinglist.modelos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.persistence.CodigoAlta;
import com.puntotres.packinglist.modelos.persistence.CodigoAltaRepository;
import com.puntotres.packinglist.modelos.persistence.Dispositivo;
import com.puntotres.packinglist.modelos.persistence.DispositivoRepository;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;

/**
 * Alta de móviles y tablets sin contraseña: el despacho genera un código de
 * seis cifras para un usuario, se teclea en el aparato y, al canjearlo, el
 * aparato recibe un token que guardará en una cookie. El token en claro se
 * entrega UNA vez; en la base de datos solo queda su SHA-256, así que quien
 * lea la base no puede hacerse pasar por ningún aparato.
 *
 * Un usuario tiene como mucho un código vivo: generar otro BORRA los
 * anteriores sin usar (no se marcan como usados, porque no lo fueron: se
 * anularon). Así un código apuntado en un papel y olvidado deja de valer en
 * cuanto se pide uno nuevo.
 */
@Service
public class Dispositivos {

    /** Lo que vale un código de alta desde que se genera. */
    public static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

    /** Lo que dura la cookie del dispositivo en el navegador. */
    public static final Duration VIGENCIA_COOKIE = Duration.ofDays(365);

    private static final String NO_VALE = "El código no vale o ha caducado";
    private static final int BYTES_TOKEN = 32;
    private static final int LARGO_NOMBRE = 120;

    private final CodigoAltaRepository codigos;
    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;
    private final SecureRandom azar = new SecureRandom();

    public Dispositivos(CodigoAltaRepository codigos, DispositivoRepository dispositivos,
                        UsuarioRepository usuarios) {
        this.codigos = codigos;
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
    }

    /** El token en claro, para la cookie, y el dispositivo que se acaba de dar de alta. */
    public record TokenDispositivo(String tokenEnClaro, Dispositivo dispositivo) {
    }

    /**
     * Un código nuevo de seis cifras (con ceros delante si toca) para ese
     * usuario, válido {@link #VIGENCIA_CODIGO}. Borra los que tuviera sin usar
     * y no repite un código que esté vivo para otro usuario: si no, canjear no
     * sabría de quién es.
     *
     * @throws IllegalArgumentException si no hay ningún usuario con ese id
     */
    @Transactional
    public String generarCodigoAlta(Long usuarioId) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new IllegalArgumentException("No hay ningún usuario con id " + usuarioId);
        }
        codigos.deleteByUsuarioIdAndUsadoEnIsNull(usuarioId);
        LocalDateTime ahora = LocalDateTime.now();
        String codigo;
        do {
            codigo = String.format("%06d", azar.nextInt(1_000_000));
        } while (vigente(codigo, ahora).isPresent());
        codigos.save(new CodigoAlta(usuarioId, codigo, ahora.plus(VIGENCIA_CODIGO)));
        return codigo;
    }

    /**
     * Canjea un código vigente: lo marca como usado y da de alta el
     * dispositivo con el hash de un token nuevo.
     *
     * @throws IllegalArgumentException con "El código no vale o ha caducado"
     *                                  si no existe, ya se usó o caducó (no se
     *                                  dice cuál)
     */
    @Transactional
    public TokenDispositivo canjear(String codigo, String nombreDispositivo) {
        LocalDateTime ahora = LocalDateTime.now();
        CodigoAlta alta = vigente(codigo == null ? null : codigo.trim(), ahora)
                .orElseThrow(() -> new IllegalArgumentException(NO_VALE));
        alta.usar(ahora);
        codigos.save(alta);

        String token = tokenNuevo();
        Dispositivo dispositivo = dispositivos.save(
                new Dispositivo(alta.getUsuarioId(), nombreDe(nombreDispositivo), hashDe(token)));
        return new TokenDispositivo(token, dispositivo);
    }

    /** El dispositivo de ese token; vacío si no existe o está revocado. */
    public Optional<Dispositivo> porToken(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            return Optional.empty();
        }
        return dispositivos.findByTokenHash(hashDe(tokenEnClaro)).filter(dispositivo -> !dispositivo.revocado());
    }

    /** Todos, también los revocados, del más reciente al más antiguo. */
    public List<Dispositivo> todos() {
        return dispositivos.findAll(Sort.by(Sort.Direction.DESC, "alta", "id"));
    }

    /**
     * Revoca el dispositivo: su cookie deja de valer. No lo borra.
     *
     * @throws IllegalArgumentException si no hay ningún dispositivo con ese id
     */
    @Transactional
    public void revocar(Long id) {
        Dispositivo dispositivo = (id == null ? Optional.<Dispositivo>empty() : dispositivos.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("No hay ningún dispositivo con id " + id));
        dispositivo.revocar();
        dispositivos.save(dispositivo);
    }

    private Optional<CodigoAlta> vigente(String codigo, LocalDateTime ahora) {
        if (codigo == null || codigo.isEmpty()) {
            return Optional.empty();
        }
        return codigos.findByCodigoAndUsadoEnIsNull(codigo).stream()
                .filter(alta -> alta.vigente(ahora))
                .findFirst();
    }

    private String tokenNuevo() {
        byte[] bytes = new byte[BYTES_TOKEN];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 del token, en hexadecimal: lo único que se guarda de él. */
    static String hashDe(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Esta JVM no tiene SHA-256", e);
        }
    }

    private static String nombreDe(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Sin nombre";
        }
        String recortado = nombre.trim();
        return recortado.length() > LARGO_NOMBRE ? recortado.substring(0, LARGO_NOMBRE) : recortado;
    }
}
