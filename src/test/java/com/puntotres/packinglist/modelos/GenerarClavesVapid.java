package com.puntotres.packinglist.modelos;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import nl.martijndwars.webpush.Utils;

/**
 * Saca un par de claves VAPID nuevo para el Web Push de la app de modelos,
 * listo para las variables {@code MODELOS_VAPID_PUBLIC} y
 * {@code MODELOS_VAPID_PRIVATE} (curva P-256, Base64 URL sin relleno, como las
 * pide el navegador):
 *
 * <pre>
 * mvn -q test-compile exec:java -Dskip.frontend=true -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.puntotres.packinglist.modelos.GenerarClavesVapid
 * </pre>
 *
 * Se hacen una vez por instalación: cambiarlas deja sin avisos a todos los
 * navegadores suscritos hasta que vuelvan a activar los avisos.
 */
public final class GenerarClavesVapid {

    private GenerarClavesVapid() {
    }

    public static void main(String[] args) throws GeneralSecurityException {
        KeyPair claves = generar();
        System.out.println("MODELOS_VAPID_PUBLIC=" + publica(claves));
        System.out.println("MODELOS_VAPID_PRIVATE=" + privada(claves));
    }

    /** Un par de claves de la curva prime256v1 (P-256), la de VAPID. */
    public static KeyPair generar() throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generador = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generador.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
        return generador.generateKeyPair();
    }

    /** La pública sin comprimir (65 bytes), en Base64 URL sin relleno. */
    public static String publica(KeyPair claves) {
        return base64Url(Utils.encode((ECPublicKey) claves.getPublic()));
    }

    /** La privada (32 bytes), en Base64 URL sin relleno. */
    public static String privada(KeyPair claves) {
        return base64Url(Utils.encode((ECPrivateKey) claves.getPrivate()));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
