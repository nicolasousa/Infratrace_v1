package br.com.infratrace.seguranca;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de senhas com PBKDF2 (já incluso no Java, sem biblioteca extra).
 *
 * Por que não guardar a senha? Se o banco vazar, ninguém deve conseguir ler as senhas.
 * Por que não um hash simples (SHA-256)? Ele é rápido demais: um atacante testa bilhões de
 * senhas por segundo. O PBKDF2 repete o cálculo 210 mil vezes, o que torna cada tentativa cara.
 * O "salt" (valor aleatório por usuário) faz senhas iguais gerarem hashes diferentes.
 *
 * Formato gravado em usr_senha_hash:  pbkdf2_sha256$iteracoes$salt$hash  (salt e hash em Base64)
 */
public final class SenhaHash {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACOES = 210_000;
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_HASH_BITS = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SenhaHash() {
    }

    public static String gerar(String senha) {
        byte[] salt = new byte[TAMANHO_SALT];
        ALEATORIO.nextBytes(salt);
        byte[] hash = calcular(senha.toCharArray(), salt, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2_sha256$" + ITERACOES + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** Recalcula o hash da senha digitada com o mesmo salt e compara com o que está no banco. */
    public static boolean confere(String senha, String hashGravado) {
        if (senha == null || hashGravado == null) {
            return false;
        }
        String[] partes = hashGravado.split("\\$");
        if (partes.length != 4 || !partes[0].equals("pbkdf2_sha256")) {
            return false;
        }
        int iteracoes = Integer.parseInt(partes[1]);
        byte[] salt = Base64.getDecoder().decode(partes[2]);
        byte[] esperado = Base64.getDecoder().decode(partes[3]);
        byte[] calculado = calcular(senha.toCharArray(), salt, iteracoes);
        // isEqual compara em tempo constante (não "vaza" em quantos bytes a comparação parou)
        return MessageDigest.isEqual(esperado, calculado);
    }

    private static byte[] calcular(char[] senha, byte[] salt, int iteracoes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha, salt, iteracoes, TAMANHO_HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular o hash da senha", e);
        }
    }
}
