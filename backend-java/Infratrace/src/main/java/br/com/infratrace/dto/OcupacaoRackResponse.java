package br.com.infratrace.dto;

import java.util.List;

/** Mapa de ocupação de um rack: quanto está ocupado, por quais ativos e quais faixas de slots estão livres. */
public record OcupacaoRackResponse(int rackId, String rackNome, int totalSlots, int slotsOcupados, int slotsLivres,
                                   double percentualOcupacao, List<SlotOcupado> ocupados,
                                   List<IntervaloLivre> livres) {

    public record SlotOcupado(int ativoId, String ativoNome, String status, int slotInicio, int slotFim, int tamanho) {
    }

    public record IntervaloLivre(int slotInicio, int slotFim, int tamanho) {
    }
}