package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.FileiraDAO;
import br.com.infratrace.dao.SalaDAO;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Fileira;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.obrigatorio;
import static br.com.infratrace.service.Validacoes.texto;

/** Regras de fileira. Uma fileira pertence a uma sala; o nome é único dentro da sala. */
@Service
public class FileiraService {

    private static final String ENTIDADE = "fileira";

    private final FileiraDAO dao;
    private final SalaDAO salaDAO;
    private final Auditoria auditoria;

    public FileiraService(FileiraDAO dao, SalaDAO salaDAO, Auditoria auditoria) {
        this.dao = dao;
        this.salaDAO = salaDAO;
        this.auditoria = auditoria;
    }

    public List<Fileira> listar(Integer salaId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, salaId);
        }
    }

    public Fileira buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Fileira criar(Fileira fileira, int usuarioId) throws SQLException {
        validar(fileira);
        return Transacao.executar(con -> {
            conferirSala(con, fileira.getSalaId());
            fileira.setId(dao.inserir(con, fileira));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, fileira.getId(), null, fileira);
            return fileira;
        });
    }

    public Fileira atualizar(int id, Fileira fileira, int usuarioId) throws SQLException {
        validar(fileira);
        return Transacao.executar(con -> {
            Fileira anterior = obter(con, id);
            conferirSala(con, fileira.getSalaId());
            fileira.setId(id);
            dao.atualizar(con, fileira);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, fileira);
            return fileira;
        });
    }

    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            Fileira anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    private Fileira obter(Connection con, int id) throws SQLException {
        Fileira fileira = dao.buscarPorId(con, id);
        if (fileira == null) {
            throw new NaoEncontradoException("Fileira " + id + " não encontrada");
        }
        return fileira;
    }

    private void conferirSala(Connection con, int salaId) throws SQLException {
        if (salaDAO.buscarPorId(con, salaId) == null) {
            throw new NegocioException("Sala " + salaId + " não existe");
        }
    }

    private void validar(Fileira fileira) {
        fileira.setNome(texto(fileira.getNome(), "nome", 10));
        obrigatorio(fileira.getSalaId(), "salaId");
    }
}
