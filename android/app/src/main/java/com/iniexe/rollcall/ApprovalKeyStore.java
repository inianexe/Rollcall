package com.iniexe.rollcall;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Locale;

final class ApprovalKeyStore {
    private ApprovalKeyStore() { }

    static boolean hasKey(String uid) throws Exception {
        KeyStore store = store();
        return store.containsAlias(alias(uid));
    }

    static String publicKey(String uid) throws Exception {
        KeyStore store = store();
        String alias = alias(uid);
        if (!store.containsAlias(alias)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(false)
                    .build());
            generator.generateKeyPair();
            store = store();
        }
        return encode(store.getCertificate(alias).getPublicKey().getEncoded());
    }

    static String sign(String uid, String message) throws Exception {
        KeyStore store = store();
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry(alias(uid), null);
        if (entry == null) throw new IllegalStateException("This phone has not created its approval key.");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(entry.getPrivateKey());
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return encode(signature.sign());
    }

    private static KeyStore store() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private static String alias(String uid) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder("rollcall_approval_v1_");
        for (int i = 0; i < 12; i++) value.append(String.format(Locale.ROOT, "%02x", digest[i]));
        return value.toString();
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
