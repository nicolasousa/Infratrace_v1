package br.com.infratrace.exception;

/** Login inválido ou sessão inexistente. Vira HTTP 401. */
public class NaoAutorizadoException extends RuntimeException {
    public NaoAutorizadoException(String mensagem) {
        super(mensagem);
    }
}
