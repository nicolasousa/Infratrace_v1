package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.TipoAtivoDAO;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.model.TipoAtivo;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.texto;
import static br.com.infratrace.service.Validacoes.textoOpcional;

/**
 * Regras de tipo_ativo. Serve de modelo para os outros cadastros simples:
 *  - leitura: abre a conexão, chama o DAO e fecha (auto-commit ligado, não precisa de transação);
 *  - escrita: Transacao.executar -> grava + registra auditoria -> commit (ou rollback se algo falhar).
 *
 * O Spring cria UM objeto desta classe (@Service) e entrega para o Controller; o DAO e a
 * Auditoria chegam pelo construtor (injeção de dependência).
 */
@Service
public class TipoAtivoService {

    private static final String ENTIDADE = "tipo_ativo";

    private final TipoAtivoDAO dao;
    private final Auditoria auditoria;

    public TipoAtivoService(TipoAtivoDAO dao, Auditoria auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

    public List<TipoAtivo> listar() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con);
        }
    }

    public TipoAtivo buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public TipoAtivo criar(TipoAtivo tipo, int usuarioId) throws SQLException {
        validar(tipo);
        return Transacao.executar(con -> {
            tipo.setId(dao.inserir(con, tipo));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, tipo.getId(), null, tipo);
            return tipo;
        });
    }

    public TipoAtivo atualizar(int id, TipoAtivo tipo, int usuarioId) throws SQLException {
        validar(tipo);
        return Transacao.executar(con -> {
            TipoAtivo anterior = obter(con, id);
            tipo.setId(id);
            dao.atualizar(con, tipo);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, tipo);
            return tipo;
        });
    }

    /** Se algum ativo usar este tipo, o MySQL recusa (erro 1451) e a API responde 409. */
    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            TipoAtivo anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    private TipoAtivo obter(Connection con, int id) throws SQLException {
        TipoAtivo tipo = dao.buscarPorId(con, id);
        if (tipo == null) {
            throw new NaoEncontradoException("Tipo de ativo " + id + " não encontrado");
        }
        return tipo;
    }

    private void validar(TipoAtivo tipo) {
        tipo.setNome(texto(tipo.getNome(), "nome", 128));
        tipo.setDescricao(textoOpcional(tipo.getDescricao(), "descricao", 512));
    }
}
