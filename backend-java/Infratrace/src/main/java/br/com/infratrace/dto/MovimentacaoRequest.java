package br.com.infratrace.dto;

/**
 * Corpo de POST /movimentacoes. A origem não é enviada: o sistema usa a posição atual do ativo.
 * Para mandar o ativo para o estoque, deixe rackDestinoId e os slots nulos.
 */
public record MovimentacaoRequest(Integer ativoId, Integer rackDestinoId, Integer slotInicioDestino,
                                  Integer slotFimDestino, String motivo) {
}