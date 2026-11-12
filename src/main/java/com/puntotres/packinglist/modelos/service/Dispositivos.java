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

import com.puntotres.packinglist.modelos.persistence.Dispositivo;
import com.puntotres.packinglist.modelos.persistence.DispositivoRepository;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;

/**
 * Los móviles y tablets del taller. Cuando un usuario del taller entra con su
 * usuario y contraseña, el aparato se da de alta y recibe un token que guarda
 * en una cookie de un año: así no tiene que volver a teclear nada. Cada
 * aparato es un alta distinta (Bachir puede entrar con la tablet y con su
 * móvil a la vez) y el admin los ve y los revoca uno a uno. El token en claro
 * se entrega UNA vez; en la base de datos solo queda su SHA-256, así que
 * quien lea la base no puede hacerse pasar por ningún aparato.
 *
 * Hasta noviembre de 2026 el aparato se daba de alta tecleando un código de
 * seis cifras que generaba el admin; el usuario prefirió el mismo login para
 * todos (la tabla del código se borró en la V9).
 */
@Service
public class Dispositivos {

    /** Lo que dura la cookie del dispositivo en el navegador. */
    public static final Duration VIGENCIA_COOKIE = Duration.ofDays(365);

    /**
     * Cada cuánto se vuelve a apuntar el último uso de un dispositivo: el
     * taller entra con la cookie en cada petición, y apuntarlo en todas sería
     * una escritura en la base de datos por cada clic.
     */
    public static final Duration CADA_CUANTO_SE_APUNTA_EL_USO = Duration.ofHours(1);

    private static final int BYTES_TOKEN = 32;
    private static final int LARGO_NOMBRE = 120;

    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;
    private final SecureRandom azar = new SecureRandom();

    public Dispositivos(DispositivoRepository dispositivos, UsuarioRepository usuarios) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
    }

    /** El token en claro, para la cookie, y el dispositivo que se acaba de dar de alta. */
    public record TokenDispositivo(String tokenEnClaro, Dispositivo dispositivo) {
    }

    /**
     * Da de alta el aparato desde el que acaba de entrar ese usuario, con el
     * hash de un token nuevo. Quien llama ya ha comprobado la contraseña.
     *
     * @throws IllegalArgumentException si no hay ningún usuario con ese id
     */
    @Transactional
    public TokenDispositivo darDeAlta(Long usuarioId, String nombreDispositivo) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new IllegalArgumentException("No hay ningún usuario con id " + usuarioId);
        }
        String token = tokenNuevo();
        Dispositivo dispositivo = dispositivos.save(new Dispositivo(usuarioId, nombreDe(nombreDispositivo), hashDe(token)));
        return new TokenDispositivo(token, dispositivo);
    }

    /** El dispositivo de ese token; vacío si no existe o está revocado. */
    public Optional<Dispositivo> porToken(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            return Optional.empty();
        }
        return dispositivos.findByTokenHash(hashDe(tokenEnClaro)).filter(dispositivo -> !dispositivo.revocado());
    }

    /**
     * Apunta que el dispositivo se acaba de usar, salvo que ya se apuntara
     * hace menos de {@link #CADA_CUANTO_SE_APUNTA_EL_USO}. Nunca deshace una
     * revocación: el dispositivo que llega es el que leyó el filtro de la
     * cookie, quizá ya viejo, así que no se guarda entero; se escribe solo el
     * último uso y solo si sigue sin revocar
     * ({@link DispositivoRepository#apuntarUso}).
     */
    @Transactional
    public void apuntarUso(Dispositivo dispositivo) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime ultimo = dispositivo.getUltimoUso();
        if (ultimo == null || ultimo.isBefore(ahora.minus(CADA_CUANTO_SE_APUNTA_EL_USO))) {
            dispositivos.apuntarUso(dispositivo.getId(), ahora);
        }
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
