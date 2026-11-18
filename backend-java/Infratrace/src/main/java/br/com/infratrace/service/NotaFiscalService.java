package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.NotaFiscalDAO;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.NotaFiscal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.cnpj;
import static br.com.infratrace.service.Validacoes.obrigatorio;
import static br.com.infratrace.service.Validacoes.somenteDigitos;
import static br.com.infratrace.service.Validacoes.texto;
import static br.com.infratrace.service.Validacoes.textoOpcional;

/** Regras de nota_fiscal. */
@Service
public class NotaFiscalService {

    private static final String ENTIDADE = "nota_fiscal";

    private final NotaFiscalDAO dao;
    private final Auditoria auditoria;

    public NotaFiscalService(NotaFiscalDAO dao, Auditoria auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

    public List<NotaFiscal> listar() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con);
        }
    }

    public NotaFiscal buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public NotaFiscal criar(NotaFiscal nota, int usuarioId) throws SQLException {
        validar(nota);
        return Transacao.executar(con -> {
            nota.setId(dao.inserir(con, nota));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, nota.getId(), null, nota);
            return nota;
        });
    }

    public NotaFiscal atualizar(int id, NotaFiscal nota, int usuarioId) throws SQLException {
        validar(nota);
        return Transacao.executar(con -> {
            NotaFiscal anterior = obter(con, id);
            nota.setId(id);
            dao.atualizar(con, nota);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, nota);
            return nota;
        });
    }

    /** Se algum ativo apontar para a nota, o MySQL recusa (1451) e a API responde 409. */
    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            NotaFiscal anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    NotaFiscal obter(Connection con, int id) throws SQLException {
        NotaFiscal nota = dao.buscarPorId(con, id);
        if (nota == null) {
            throw new NaoEncontradoException("Nota fiscal " + id + " não encontrada");
        }
        return nota;
    }

    private void validar(NotaFiscal nota) {
        if (obrigatorio(nota.getNum(), "num") <= 0) {
            throw new NegocioException("O número da nota deve ser positivo");
        }
        if (obrigatorio(nota.getValor(), "valor").compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("O valor da nota não pode ser negativo");
        }
        if (nota.getVolumes() != null && nota.getVolumes() < 0) {
            throw new NegocioException("O número de volumes não pode ser negativo");
        }
        String chave = somenteDigitos(textoOpcional(nota.getChave(), "chave", 60));
        if (chave != null && chave.length() != 44) {
            throw new NegocioException("A chave de acesso da NF-e deve ter 44 dígitos");
        }
        nota.setChave(chave);
        nota.setRazaoSocial(texto(nota.getRazaoSocial(), "razaoSocial", 128));
        nota.setCnpj(cnpj(nota.getCnpj(), "cnpj"));
        nota.setEndereco(textoOpcional(nota.getEndereco(), "endereco", 128));
        nota.setInscricao(textoOpcional(nota.getInscricao(), "inscricao", 128));
        nota.setDescricao(textoOpcional(nota.getDescricao(), "descricao", 128));
    }
}
