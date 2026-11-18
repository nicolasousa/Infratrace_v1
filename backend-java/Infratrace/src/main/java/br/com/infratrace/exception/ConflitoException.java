package br.com.infratrace.exception;

/** A operação conflita com o estado atual dos dados (slot ocupado, e-mail repetido...). Vira HTTP 409. */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
