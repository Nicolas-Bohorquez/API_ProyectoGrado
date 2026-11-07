package com.example.AppSegurity.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utilidad de cifrado autenticado AES-GCM (256/128 bits) con vector de inicialización aleatorio (IV).
 * Mantiene compatibilidad hacia atrás con datos legados cifrados en modo ECB.
 */
public class AESEncryptionUtil {

    private static final String LLAVE_AES_LEGADA = "ProyectoGrado123";
    private static final String LLAVE_AES = resolverLlave();
    private static final int GCM_IV_LENGTH = 12; // 96 bits estándar NIST para GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits de autenticación de integridad

    private static String resolverLlave() {
        String llave = System.getenv("AES_LLAVE");
        if (llave == null || (llave.length() != 16 && llave.length() != 24 && llave.length() != 32)) {
            System.err.println("[SEGURIDAD] AES_LLAVE no definida o longitud distinta de 16/24/32: se usa llave de contingencia.");
            return LLAVE_AES_LEGADA;
        }
        return llave;
    }

    public static String encriptarDatoSensible(String dato) {
        if (dato == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec secretKey = new SecretKeySpec(LLAVE_AES.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(dato.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Error cifrando los datos sensibles: " + e.getMessage(), e);
        }
    }

    public static String desencriptarDatoSensible(String datoEncriptado) {
        if (datoEncriptado == null || datoEncriptado.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(datoEncriptado);

            // Intentar descifrar primero con AES-GCM (formato moderno: IV 12 bytes + Ciphertext + Tag)
            if (decoded.length > GCM_IV_LENGTH + 16) {
                try {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
                    byte[] iv = new byte[GCM_IV_LENGTH];
                    byteBuffer.get(iv);
                    byte[] cipherText = new byte[byteBuffer.remaining()];
                    byteBuffer.get(cipherText);

                    SecretKeySpec secretKey = new SecretKeySpec(LLAVE_AES.getBytes(StandardCharsets.UTF_8), "AES");
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

                    return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
                } catch (Exception eGcm) {
                    // Si falla GCM, cae al descifrado legado ECB
                }
            }

            // Fallback: Descifrado legado ECB para datos antiguos
            SecretKeySpec secretKeyLegada = new SecretKeySpec(LLAVE_AES.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipherEcb = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipherEcb.init(Cipher.DECRYPT_MODE, secretKeyLegada);
            return new String(cipherEcb.doFinal(decoded), StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Error descifrando los datos sensibles: " + e.getMessage(), e);
        }
    }
}
