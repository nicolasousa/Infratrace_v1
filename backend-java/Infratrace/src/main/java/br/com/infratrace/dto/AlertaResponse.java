package br.com.infratrace.dto;

import java.time.LocalDate;
import java.util.List;

/** Resultado dos alertas: o prazo usado (vindo da tabela configuracao) e os itens encontrados. */
public record AlertaResponse<T>(int diasAntecedencia, LocalDate ate, int quantidade, List<T> itens) {
}