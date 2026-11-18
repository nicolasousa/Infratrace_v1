package br.com.infratrace.seguranca;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda as sessões abertas em memória: token -> (id do usuário, validade).
 *
 * Simplificação consciente para o projeto acadêmico: as sessões somem quando a aplicação
 * reinicia (basta fazer login de novo). Em produção usaríamos JWT ou uma tabela de sessões.
 * ConcurrentHashMap porque várias requisições HTTP chegam ao mesmo tempo, em threads diferentes.
 */
@Component
public class Sessoes {

    private static final Duration VALIDADE = Duration.ofHours(8);

    private record Sessao(int usuarioId, Instant expiraEm) {
    }

    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();

    public String abrir(int usuarioId) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessoes.put(token, new Sessao(usuarioId, Instant.now().plus(VALIDADE)));
        return token;
    }

    /** Devolve o id do usuário dono do token, ou null se o token não existir ou tiver expirado. */
    public Integer usuarioDoToken(String token) {
        Sessao sessao = sessoes.get(token);
        if (sessao == null) {
            return null;
        }
        if (sessao.expiraEm().isBefore(Instant.now())) {
            sessoes.remove(token);
            return null;
        }
        return sessao.usuarioId();
    }

    public void encerrar(String token) {
        sessoes.remove(token);
    }

    /** Usado quando um usuário é inativado: derruba todas as sessões dele. */
    public void encerrarTodasDoUsuario(int usuarioId) {
        sessoes.values().removeIf(s -> s.usuarioId() == usuarioId);
    }
}
