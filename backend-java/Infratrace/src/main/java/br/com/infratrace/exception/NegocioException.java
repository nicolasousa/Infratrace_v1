package br.com.infratrace.exception;

/** Dados inválidos enviados pelo cliente (campo obrigatório, valor fora da faixa...). Vira HTTP 400. */
public class NegocioException extends RuntimeException {
    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
