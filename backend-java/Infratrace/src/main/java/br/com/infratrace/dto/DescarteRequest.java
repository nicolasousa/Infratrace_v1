package br.com.infratrace.dto;

import java.time.LocalDate;

/** Corpo de POST /ativos/{id}/descarte. Se a data não vier, usa a data de hoje. */
public record DescarteRequest(LocalDate data, String motivo) {
}