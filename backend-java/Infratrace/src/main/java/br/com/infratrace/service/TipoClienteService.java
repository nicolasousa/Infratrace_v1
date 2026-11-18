package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.TipoClienteDAO;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.model.TipoCliente;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.texto;

/** Regras de tipo_cliente (mesmo padrão do TipoAtivoService). */
@Service
public class TipoClienteService {

    private static final String ENTIDADE = "tipo_cliente";

    private final TipoClienteDAO dao;
    private final Auditoria auditoria;

    public TipoClienteService(TipoClienteDAO dao, Auditoria auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

    public List<TipoCliente> listar() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con);
        }
    }

    public TipoCliente buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public TipoCliente criar(TipoCliente tipo, int usuarioId) throws SQLException {
        tipo.setNome(texto(tipo.getNome(), "nome", 128));
        return Transacao.executar(con -> {
            tipo.setId(dao.inserir(con, tipo));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, tipo.getId(), null, tipo);
            return tipo;
        });
    }

    public TipoCliente atualizar(int id, TipoCliente tipo, int usuarioId) throws SQLException {
        tipo.setNome(texto(tipo.getNome(), "nome", 128));
        return Transacao.executar(con -> {
            TipoCliente anterior = obter(con, id);
            tipo.setId(id);
            dao.atualizar(con, tipo);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, tipo);
            return tipo;
        });
    }

    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            TipoCliente anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    TipoCliente obter(Connection con, int id) throws SQLException {
        TipoCliente tipo = dao.buscarPorId(con, id);
        if (tipo == null) {
            throw new NaoEncontradoException("Tipo de cliente " + id + " não encontrado");
        }
        return tipo;
    }
}
