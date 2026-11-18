package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.SalaDAO;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.model.Sala;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.texto;
import static br.com.infratrace.service.Validacoes.textoOpcional;

/** Regras de sala. */
@Service
public class SalaService {

    private static final String ENTIDADE = "sala";

    private final SalaDAO dao;
    private final Auditoria auditoria;

    public SalaService(SalaDAO dao, Auditoria auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

    public List<Sala> listar() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con);
        }
    }

    public Sala buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Sala criar(Sala sala, int usuarioId) throws SQLException {
        validar(sala);
        return Transacao.executar(con -> {
            sala.setId(dao.inserir(con, sala));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, sala.getId(), null, sala);
            return sala;
        });
    }

    public Sala atualizar(int id, Sala sala, int usuarioId) throws SQLException {
        validar(sala);
        return Transacao.executar(con -> {
            Sala anterior = obter(con, id);
            sala.setId(id);
            dao.atualizar(con, sala);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, sala);
            return sala;
        });
    }

    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            Sala anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    Sala obter(Connection con, int id) throws SQLException {
        Sala sala = dao.buscarPorId(con, id);
        if (sala == null) {
            throw new NaoEncontradoException("Sala " + id + " não encontrada");
        }
        return sala;
    }

    private void validar(Sala sala) {
        sala.setNome(texto(sala.getNome(), "nome", 128));
        sala.setDescricao(textoOpcional(sala.getDescricao(), "descricao", 512));
    }
}
