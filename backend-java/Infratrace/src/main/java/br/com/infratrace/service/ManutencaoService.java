package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.AtivoDAO;
import br.com.infratrace.dao.ManutencaoDAO;
import br.com.infratrace.dto.FinalizarManutencaoRequest;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Ativo;
import br.com.infratrace.model.Manutencao;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static br.com.infratrace.service.Validacoes.obrigatorio;
import static br.com.infratrace.service.Validacoes.opcao;
import static br.com.infratrace.service.Validacoes.texto;

/**
 * Regras de manutenção.
 *  - Abrir uma manutenção sem data de fim coloca o ativo em EM_MANUTENCAO.
 *  - Finalizar (PATCH /manutencoes/{id}/finalizar) volta o ativo para ATIVO, se não houver outra aberta.
 *  - Um ativo só pode ter uma manutenção em andamento por vez.
 * Manutenção + status do ativo + auditoria acontecem na mesma transação.
 */
@Service
public class ManutencaoService {

    private static final String ENTIDADE = "manutencao";
    private static final Set<String> TIPOS = Set.of("PREVENTIVA", "CORRETIVA");
    private static final String EM_MANUTENCAO = "EM_MANUTENCAO";

    private final ManutencaoDAO dao;
    private final AtivoDAO ativoDAO;
    private final AtivoService ativoService;
    private final Auditoria auditoria;

    public ManutencaoService(ManutencaoDAO dao, AtivoDAO ativoDAO, AtivoService ativoService, Auditoria auditoria) {
        this.dao = dao;
        this.ativoDAO = ativoDAO;
        this.ativoService = ativoService;
        this.auditoria = auditoria;
    }

    public List<Manutencao> listar(Integer ativoId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, ativoId);
        }
    }

    public Manutencao buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Manutencao criar(Manutencao m, int usuarioId) throws SQLException {
        validar(m);
        obrigatorio(m.getAtivoId(), "ativoId");
        return Transacao.executar(con -> {
            Ativo ativo = ativoService.obterParaAtualizar(con, m.getAtivoId());
            if (AtivoService.DESCARTADO.equals(ativo.getStatus())) {
                throw new ConflitoException("Ativo descartado não pode receber manutenção");
            }
            boolean emAndamento = m.getDataFim() == null;
            if (emAndamento && dao.contarEmAndamento(con, ativo.getId(), null) > 0) {
                throw new ConflitoException("O ativo já tem uma manutenção em andamento; finalize-a antes");
            }
            m.setUsuarioId(usuarioId);
            m.setId(dao.inserir(con, m));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, m.getId(), null, m);

            if (emAndamento && !EM_MANUTENCAO.equals(ativo.getStatus())) {
                ativoDAO.atualizarStatus(con, ativo.getId(), EM_MANUTENCAO);
                auditoria.registrar(con, usuarioId, Auditoria.UPDATE, "ativo", ativo.getId(),
                        ativo, ativoDAO.buscarPorId(con, ativo.getId()));
            }
            return m;
        });
    }

    /** Altera tipo, descrição, responsável e data de início. A data de fim muda só por /finalizar. */
    public Manutencao atualizar(int id, Manutencao m, int usuarioId) throws SQLException {
        validar(m);
        return Transacao.executar(con -> {
            Manutencao anterior = obter(con, id);
            if (anterior.getDataFim() != null && m.getDataInicio().isAfter(anterior.getDataFim())) {
                throw new NegocioException("A data de início não pode ser posterior à data de fim");
            }
            m.setId(id);
            m.setDataFim(anterior.getDataFim());
            m.setAtivoId(anterior.getAtivoId());
            m.setUsuarioId(anterior.getUsuarioId());
            dao.atualizar(con, m);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, m);
            return m;
        });
    }

    public Manutencao finalizar(int id, FinalizarManutencaoRequest pedido, int usuarioId) throws SQLException {
        LocalDate dataFim = pedido != null && pedido.dataFim() != null ? pedido.dataFim() : LocalDate.now();
        return Transacao.executar(con -> {
            Manutencao anterior = obter(con, id);
            if (anterior.getDataFim() != null) {
                throw new ConflitoException("Manutenção já finalizada em " + anterior.getDataFim());
            }
            if (dataFim.isBefore(anterior.getDataInicio())) {
                throw new NegocioException("A data de fim não pode ser anterior ao início (" + anterior.getDataInicio() + ")");
            }
            Ativo ativo = ativoService.obterParaAtualizar(con, anterior.getAtivoId());

            Manutencao finalizada = dao.buscarPorId(con, id);
            finalizada.setDataFim(dataFim);
            dao.atualizar(con, finalizada);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, finalizada);

            if (EM_MANUTENCAO.equals(ativo.getStatus()) && dao.contarEmAndamento(con, ativo.getId(), id) == 0) {
                ativoDAO.atualizarStatus(con, ativo.getId(), "ATIVO");
                auditoria.registrar(con, usuarioId, Auditoria.UPDATE, "ativo", ativo.getId(),
                        ativo, ativoDAO.buscarPorId(con, ativo.getId()));
            }
            return finalizada;
        });
    }

    private Manutencao obter(Connection con, int id) throws SQLException {
        Manutencao m = dao.buscarPorId(con, id);
        if (m == null) {
            throw new NaoEncontradoException("Manutenção " + id + " não encontrada");
        }
        return m;
    }

    private void validar(Manutencao m) {
        m.setTipo(opcao(m.getTipo(), "tipo", TIPOS));
        m.setDescricao(texto(m.getDescricao(), "descricao", 512));
        m.setResponsavel(texto(m.getResponsavel(), "responsavel", 128));
        obrigatorio(m.getDataInicio(), "dataInicio");
        if (m.getDataFim() != null && m.getDataFim().isBefore(m.getDataInicio())) {
            throw new NegocioException("A data de fim não pode ser anterior à data de início");
        }
    }
}
