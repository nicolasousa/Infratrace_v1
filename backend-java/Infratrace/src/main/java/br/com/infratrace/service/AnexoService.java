package br.com.infratrace.service;

import br.com.infratrace.config.ArmazenamentoDeArquivos;
import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.AnexoDAO;
import br.com.infratrace.dto.AnexoResponse;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Anexo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static br.com.infratrace.service.Validacoes.opcao;

/**
 * Anexos de ativos (RF-01.5).
 *
 * Detalhe importante: o ARQUIVO fica no disco e os DADOS dele no banco. O disco não participa
 * da transação do MySQL (não existe rollback de arquivo). Por isso a ordem das operações importa:
 *
 *  - Enviar:  grava o arquivo -> transação (INSERT anexo + log) -> commit.
 *             Se a transação falhar, o arquivo recém-gravado é apagado (compensação),
 *             para não sobrar arquivo "órfão" sem registro no banco.
 *  - Excluir: transação (DELETE anexo + log) -> commit -> apaga o arquivo.
 *             Se apagar o arquivo falhar, sobra um arquivo sem uso, mas o banco fica correto.
 */
@Service
public class AnexoService {

    private static final String ENTIDADE = "anexo";
    public static final Set<String> TIPOS = Set.of("NOTA_FISCAL", "MANUAL", "CONTRATO", "OUTRO");
    public static final Set<String> EXTENSOES = Set.of(
            "pdf", "png", "jpg", "jpeg", "txt", "csv", "doc", "docx", "xls", "xlsx", "odt", "ods");

    private final AnexoDAO dao;
    private final AtivoService ativoService;
    private final ArmazenamentoDeArquivos armazenamento;
    private final Auditoria auditoria;

    public AnexoService(AnexoDAO dao, AtivoService ativoService, ArmazenamentoDeArquivos armazenamento,
                        Auditoria auditoria) {
        this.dao = dao;
        this.ativoService = ativoService;
        this.armazenamento = armazenamento;
        this.auditoria = auditoria;
    }

    /** Arquivo pronto para download: os dados do banco + o caminho no disco. */
    public record ArquivoAnexo(Anexo anexo, Path caminho) {
    }

    public List<AnexoResponse> listarPorAtivo(int ativoId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            ativoService.obter(con, ativoId);                       // 404 se o ativo não existir
            return dao.listarPorAtivo(con, ativoId).stream().map(this::resposta).toList();
        }
    }

    public AnexoResponse buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return resposta(obter(con, id));
        }
    }

    public AnexoResponse anexar(int ativoId, String tipo, MultipartFile arquivo, int usuarioId)
            throws SQLException, IOException {
        String tipoValido = opcao(tipo, "tipo", TIPOS);
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie um arquivo não vazio no campo 'arquivo'");
        }
        String nomeOriginal = nomeOriginal(arquivo.getOriginalFilename());
        String extensao = extensao(nomeOriginal);

        // Confere o ativo antes de gravar qualquer coisa no disco.
        try (Connection con = Conexao.getConexao()) {
            ativoService.obter(con, ativoId);
        }

        String nomeNoDisco = armazenamento.salvar(arquivo, extensao);
        try {
            return Transacao.executar(con -> {
                ativoService.obterParaAtualizar(con, ativoId);     // trava o ativo durante o INSERT
                Anexo anexo = new Anexo();
                anexo.setTipo(tipoValido);
                anexo.setNomeOriginal(nomeOriginal);
                anexo.setCaminho(nomeNoDisco);
                anexo.setAtivoId(ativoId);
                int id = dao.inserir(con, anexo);
                Anexo salvo = dao.buscarPorId(con, id);
                auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, id, null, salvo);
                return resposta(salvo);
            });
        } catch (SQLException | RuntimeException e) {
            armazenamento.excluir(nomeNoDisco);                     // compensação: desfaz o arquivo
            throw e;
        }
    }

    public ArquivoAnexo arquivo(int id) throws SQLException {
        Anexo anexo;
        try (Connection con = Conexao.getConexao()) {
            anexo = obter(con, id);
        }
        if (!armazenamento.existe(anexo.getCaminho())) {
            throw new NaoEncontradoException("O arquivo do anexo " + id + " não foi encontrado no servidor");
        }
        return new ArquivoAnexo(anexo, armazenamento.localizar(anexo.getCaminho()));
    }

    public void excluir(int id, int usuarioId) throws SQLException {
        Anexo removido = Transacao.executar(con -> {
            Anexo anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return anterior;
        });
        armazenamento.excluir(removido.getCaminho());              // só depois do commit
    }

    private Anexo obter(Connection con, int id) throws SQLException {
        Anexo anexo = dao.buscarPorId(con, id);
        if (anexo == null) {
            throw new NaoEncontradoException("Anexo " + id + " não encontrado");
        }
        return anexo;
    }

    private AnexoResponse resposta(Anexo a) {
        return AnexoResponse.de(a, armazenamento.tamanho(a.getCaminho()));
    }

    /** Alguns navegadores mandam o caminho completo ("C:\docs\nf.pdf"): fica só o nome. */
    private static String nomeOriginal(String enviado) {
        if (enviado == null || enviado.isBlank()) {
            throw new NegocioException("O arquivo enviado não tem nome");
        }
        String nome = Paths.get(enviado.replace('\\', '/').replaceAll(".*/", "")).toString().trim();
        if (nome.isEmpty() || nome.length() > 255) {
            throw new NegocioException("O nome do arquivo deve ter entre 1 e 255 caracteres");
        }
        return nome;
    }

    private static String extensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        String ext = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSOES.contains(ext)) {
            throw new NegocioException("Tipo de arquivo não permitido. Extensões aceitas: " + EXTENSOES);
        }
        return ext;
    }
}
