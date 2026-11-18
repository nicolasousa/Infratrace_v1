package br.com.infratrace.dto;

import br.com.infratrace.model.Anexo;

import java.time.LocalDateTime;

/**
 * Anexo como sai da API. Não expõe o caminho interno do arquivo no servidor;
 * em vez disso, informa a rota de download.
 */
public record AnexoResponse(Integer id, String tipo, String nomeOriginal, Long tamanhoBytes,
                            LocalDateTime enviadoEm, Integer ativoId, String download) {

    public static AnexoResponse de(Anexo a, Long tamanhoBytes) {
        return new AnexoResponse(a.getId(), a.getTipo(), a.getNomeOriginal(), tamanhoBytes, a.getEnviadoEm(),
                a.getAtivoId(), "/anexos/" + a.getId() + "/download");
    }
}
