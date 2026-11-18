package br.com.infratrace.dto;

import java.time.LocalDateTime;

/** Formato padrão de todas as respostas de erro da API. */
public record RespostaErro(int status, String erro, String mensagem, LocalDateTime dataHora) {

    public static RespostaErro de(int status, String erro, String mensagem) {
        return new RespostaErro(status, erro, mensagem, LocalDateTime.now());
    }
}
