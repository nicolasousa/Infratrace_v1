package br.com.infratrace.dto;

import java.time.LocalDate;

/** Corpo de PATCH /manutencoes/{id}/finalizar. Se a data não vier, usa a data de hoje. */
public record FinalizarManutencaoRequest(LocalDate dataFim) {
}