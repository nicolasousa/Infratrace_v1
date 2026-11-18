package br.com.infratrace.model;

import java.time.LocalDateTime;

/**
 * Tabela anexo: documentos de um ativo (nota fiscal, manual, contrato...).
 * O arquivo em si fica numa pasta do servidor; o banco guarda os dados dele e o caminho.
 */
public class Anexo {

    private Integer id;
    private String tipo;
    private String nomeOriginal;
    private String caminho;
    private LocalDateTime enviadoEm;
    private Integer ativoId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public void setNomeOriginal(String nomeOriginal) {
        this.nomeOriginal = nomeOriginal;
    }

    public String getCaminho() {
        return caminho;
    }

    public void setCaminho(String caminho) {
        this.caminho = caminho;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public void setEnviadoEm(LocalDateTime enviadoEm) {
        this.enviadoEm = enviadoEm;
    }

    public Integer getAtivoId() {
        return ativoId;
    }

    public void setAtivoId(Integer ativoId) {
        this.ativoId = ativoId;
    }
}
