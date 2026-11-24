package br.com.infratrace.dto;

/** Uma linha da view vw_ocupacao_rack: o resumo de ocupação de um rack e onde ele fica. */
public record ResumoOcupacaoRack(int rackId, String rackNome, int fileiraId, String fileiraNome, int salaId,
                                 String salaNome, int totalSlots, int qtdAtivos, int slotsOcupados,
                                 int slotsLivres, double percentualOcupacao) {
}
