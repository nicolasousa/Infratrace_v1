package br.com.infratrace.dto;

import java.time.LocalDate;

/**
 * Uma linha da view vw_garantia_ativo: o ativo, quantos dias faltam para a garantia vencer e onde
 * ele está. rackId, rackNome, fileiraNome, salaNome e os slots vêm null quando o ativo está no estoque.
 */
public record GarantiaVencendo(int ativoId, String ativoNome, String patrimonio, String status, LocalDate garantia,
                               int diasRestantes, String tipo, Integer rackId, String rackNome, String fileiraNome,
                               String salaNome, Integer slotInicio, Integer slotFim) {
}
