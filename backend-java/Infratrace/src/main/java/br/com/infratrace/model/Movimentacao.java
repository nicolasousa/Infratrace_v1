package br.com.infratrace.model;

import java.time.LocalDateTime;

/**
 * Tabela movimentacao: histórico de mudanças físicas de um ativo.
 * Origem nula = o ativo veio do estoque. Destino nulo = o ativo foi para o estoque.
 */
public class Movimentacao {

    private Integer id;
    private LocalDateTime data;
    private String motivo;
    private Integer rackOrigemId;
    private Integer slotInicioOrigem;
    private Integer slotFimOrigem;
    private Integer rackDestinoId;
    private Integer slotInicioDestino;
    private Integer slotFimDestino;
    private Integer ativoId;
    private Integer usuarioId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Integer getRackOrigemId() {
        return rackOrigemId;
    }

    public void setRackOrigemId(Integer rackOrigemId) {
        this.rackOrigemId = rackOrigemId;
    }

    public Integer getSlotInicioOrigem() {
        return slotInicioOrigem;
    }

    public void setSlotInicioOrigem(Integer slotInicioOrigem) {
        this.slotInicioOrigem = slotInicioOrigem;
    }

    public Integer getSlotFimOrigem() {
        return slotFimOrigem;
    }

    public void setSlotFimOrigem(Integer slotFimOrigem) {
        this.slotFimOrigem = slotFimOrigem;
    }

    public Integer getRackDestinoId() {
        return rackDestinoId;
    }

    public void setRackDestinoId(Integer rackDestinoId) {
        this.rackDestinoId = rackDestinoId;
    }

    public Integer getSlotInicioDestino() {
        return slotInicioDestino;
    }

    public void setSlotInicioDestino(Integer slotInicioDestino) {
        this.slotInicioDestino = slotInicioDestino;
    }

    public Integer getSlotFimDestino() {
        return slotFimDestino;
    }

    public void setSlotFimDestino(Integer slotFimDestino) {
        this.slotFimDestino = slotFimDestino;
    }

    public Integer getAtivoId() {
        return ativoId;
    }

    public void setAtivoId(Integer ativoId) {
        this.ativoId = ativoId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Integer usuarioId) {
        this.usuarioId = usuarioId;
    }
}
