package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.ClienteDAO;
import br.com.infratrace.dao.TipoClienteDAO;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Cliente;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.cnpj;
import static br.com.infratrace.service.Validacoes.obrigatorio;
import static br.com.infratrace.service.Validacoes.texto;

/**
 * Regras de cliente. O CNPJ é validado (dígitos verificadores) e gravado só com números.
 * Clientes não são excluídos, são inativados, para não perder o histórico de aluguéis.
 */
@Service
public class ClienteService {

    private static final String ENTIDADE = "cliente";

    private final ClienteDAO dao;
    private final TipoClienteDAO tipoClienteDAO;
    private final Auditoria auditoria;

    public ClienteService(ClienteDAO dao, TipoClienteDAO tipoClienteDAO, Auditoria auditoria) {
        this.dao = dao;
        this.tipoClienteDAO = tipoClienteDAO;
        this.auditoria = auditoria;
    }

    public List<Cliente> listar(Boolean ativo) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, ativo);
        }
    }

    public Cliente buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Cliente criar(Cliente cliente, int usuarioId) throws SQLException {
        validar(cliente);
        cliente.setAtivo(true);
        return Transacao.executar(con -> {
            conferir(con, cliente, null);
            cliente.setId(dao.inserir(con, cliente));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, cliente.getId(), null, cliente);
            return cliente;
        });
    }

    public Cliente atualizar(int id, Cliente cliente, int usuarioId) throws SQLException {
        validar(cliente);
        return Transacao.executar(con -> {
            Cliente anterior = obter(con, id);
            conferir(con, cliente, id);
            cliente.setId(id);
            if (cliente.getAtivo() == null) {
                cliente.setAtivo(anterior.getAtivo());
            }
            dao.atualizar(con, cliente);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, cliente);
            return cliente;
        });
    }

    public Cliente inativar(int id, int usuarioId) throws SQLException {
        return Transacao.executar(con -> {
            Cliente anterior = obter(con, id);
            if (!anterior.getAtivo()) {
                throw new ConflitoException("O cliente já está inativo");
            }
            Cliente atualizado = obter(con, id);
            atualizado.setAtivo(false);
            dao.atualizar(con, atualizado);
            auditoria.registrar(con, usuarioId, Auditoria.INATIVAR, ENTIDADE, id, anterior, atualizado);
            return atualizado;
        });
    }

    Cliente obter(Connection con, int id) throws SQLException {
        Cliente cliente = dao.buscarPorId(con, id);
        if (cliente == null) {
            throw new NaoEncontradoException("Cliente " + id + " não encontrado");
        }
        return cliente;
    }

    private void conferir(Connection con, Cliente cliente, Integer idAtual) throws SQLException {
        if (tipoClienteDAO.buscarPorId(con, cliente.getTipoClienteId()) == null) {
            throw new NegocioException("Tipo de cliente " + cliente.getTipoClienteId() + " não existe");
        }
        Cliente mesmoCnpj = dao.buscarPorCnpj(con, cliente.getCnpj());
        if (mesmoCnpj != null && !mesmoCnpj.getId().equals(idAtual)) {
            throw new ConflitoException("Já existe cliente com este CNPJ: " + mesmoCnpj.getNome() + " (id " + mesmoCnpj.getId() + ")");
        }
    }

    private void validar(Cliente cliente) {
        cliente.setNome(texto(cliente.getNome(), "nome", 200));
        cliente.setCnpj(cnpj(cliente.getCnpj(), "cnpj"));
        cliente.setRepresentante(texto(cliente.getRepresentante(), "representante", 128));
        obrigatorio(cliente.getTipoClienteId(), "tipoClienteId");
    }
}
