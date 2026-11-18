package br.com.infratrace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Grava, localiza e apaga os arquivos dos anexos numa pasta do servidor.
 *
 * A pasta vem da propriedade infratrace.anexos.diretorio (application.properties), que por sua vez
 * lê a variável de ambiente INFRATRACE_ANEXOS_DIR (padrão: "anexos", relativo à pasta onde a
 * aplicação foi iniciada).
 *
 * Segurança: o arquivo é salvo com um nome gerado (UUID), nunca com o nome enviado pelo usuário.
 * Um nome malicioso como "../../config.txt" não consegue escrever fora da pasta.
 */
@Component
public class ArmazenamentoDeArquivos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoDeArquivos.class);

    private final Path diretorio;

    public ArmazenamentoDeArquivos(@Value("${infratrace.anexos.diretorio}") String diretorio) throws IOException {
        this.diretorio = Paths.get(diretorio).toAbsolutePath().normalize();
        Files.createDirectories(this.diretorio);
        log.info("Anexos serão gravados em {}", this.diretorio);
    }

    /** Salva o arquivo e devolve o nome gerado (é isso que vai para anx_caminho). */
    public String salvar(MultipartFile arquivo, String extensao) throws IOException {
        String nome = UUID.randomUUID() + "." + extensao;
        arquivo.transferTo(localizar(nome));
        return nome;
    }

    /** Caminho completo do arquivo, garantindo que ele está dentro da pasta de anexos. */
    public Path localizar(String nome) {
        Path caminho = diretorio.resolve(nome).normalize();
        if (!caminho.startsWith(diretorio)) {
            throw new IllegalStateException("Caminho de anexo inválido");
        }
        return caminho;
    }

    /** Tamanho em bytes, ou null se o arquivo não estiver mais no disco. */
    public Long tamanho(String nome) {
        try {
            return Files.size(localizar(nome));
        } catch (IOException e) {
            return null;
        }
    }

    public boolean existe(String nome) {
        return Files.isRegularFile(localizar(nome));
    }

    /** Apaga sem lançar exceção: uma falha aqui não deve desfazer o que já foi gravado no banco. */
    public void excluir(String nome) {
        try {
            Files.deleteIfExists(localizar(nome));
        } catch (IOException e) {
            log.warn("Não foi possível apagar o arquivo de anexo {}", nome, e);
        }
    }
}
