package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.AluguelDAO;
import br.com.infratrace.dao.RackDAO;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Aluguel;
import br.com.infratrace.model.Cliente;
import br.com.infratrace.model.Rack;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.infratrace.service.Validacoes.obrigatorio;

/**
 * Regras de aluguel de rack (RF-07).
 *  - período válido (fim >= início) e valor >= 0;
 *  - cliente precisa existir e estar ativo;
 *  - o mesmo rack não pode ter dois aluguéis com períodos sobrepostos (RF-07.2).
 *    O trigger tg_aluguel_periodo_* garante isso no banco; aqui a mensagem diz qual aluguel conflita.
 */
@Service
public class AluguelService {

    private static final String ENTIDADE = "aluguel";

    private final AluguelDAO dao;
    private final RackDAO rackDAO;
    private final ClienteService clienteService;
    private final Auditoria auditoria;

    public AluguelService(AluguelDAO dao, RackDAO rackDAO, ClienteService clienteService, Auditoria auditoria) {
        this.dao = dao;
        this.rackDAO = rackDAO;
        this.clienteService = clienteService;
        this.auditoria = auditoria;
    }

    public List<Aluguel> listar(Integer clienteId, Integer rackId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, clienteId, rackId);
        }
    }

    public Aluguel buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Aluguel criar(Aluguel aluguel, int usuarioId) throws SQLException {
        validar(aluguel);
        return Transacao.executar(con -> {
            conferir(con, aluguel, null);
            aluguel.setId(dao.inserir(con, aluguel));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, aluguel.getId(), null, aluguel);
            return aluguel;
        });
    }

    public Aluguel atualizar(int id, Aluguel aluguel, int usuarioId) throws SQLException {
        validar(aluguel);
        return Transacao.executar(con -> {
            Aluguel anterior = obter(con, id);
            conferir(con, aluguel, id);
            aluguel.setId(id);
            dao.atualizar(con, aluguel);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, aluguel);
            return aluguel;
        });
    }

    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            Aluguel anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    private Aluguel obter(Connection con, int id) throws SQLException {
        Aluguel aluguel = dao.buscarPorId(con, id);
        if (aluguel == null) {
            throw new NaoEncontradoException("Aluguel " + id + " não encontrado");
        }
        return aluguel;
    }

    /** Trava o rack (FOR UPDATE) para que dois aluguéis simultâneos do mesmo rack não passem juntos. */
    private void conferir(Connection con, Aluguel aluguel, Integer ignorarId) throws SQLException {
        Cliente cliente = clienteService.obter(con, aluguel.getClienteId());
        if (!cliente.getAtivo()) {
            throw new ConflitoException("O cliente " + cliente.getNome() + " está inativo");
        }
        Rack rack = rackDAO.buscarPorIdParaAtualizar(con, aluguel.getRackId());
        if (rack == null) {
            throw new NegocioException("Rack " + aluguel.getRackId() + " não existe");
        }
        Aluguel conflito = dao.buscarSobreposicao(con, aluguel.getRackId(), aluguel.getDataInicio(),
                aluguel.getDataFim(), ignorarId);
        if (conflito != null) {
            throw new ConflitoException("O rack " + rack.getNome() + " já está alugado de " + conflito.getDataInicio()
                    + " a " + conflito.getDataFim() + " (aluguel " + conflito.getId() + ")");
        }
    }

    private void validar(Aluguel aluguel) {
        obrigatorio(aluguel.getDataInicio(), "dataInicio");
        obrigatorio(aluguel.getDataFim(), "dataFim");
        obrigatorio(aluguel.getClienteId(), "clienteId");
        obrigatorio(aluguel.getRackId(), "rackId");
        if (aluguel.getDataFim().isBefore(aluguel.getDataInicio())) {
            throw new NegocioException("A data de fim não pode ser anterior à data de início");
        }
        if (obrigatorio(aluguel.getValor(), "valor").compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("O valor do aluguel não pode ser negativo");
        }
    }
}
