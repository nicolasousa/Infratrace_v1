package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.AtivoDAO;
import br.com.infratrace.dao.FileiraDAO;
import br.com.infratrace.dao.RackDAO;
import br.com.infratrace.dto.OcupacaoRackResponse;
import br.com.infratrace.dto.OcupacaoRackResponse.IntervaloLivre;
import br.com.infratrace.dto.OcupacaoRackResponse.SlotOcupado;
import br.com.infratrace.dto.ResumoOcupacaoRack;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Ativo;
import br.com.infratrace.model.Rack;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static br.com.infratrace.service.Validacoes.obrigatorio;
import static br.com.infratrace.service.Validacoes.texto;

/**
 * Regras de rack e da OCUPAÇÃO dos racks.
 *
 * As mesmas regras também existem como triggers no MySQL (última linha de defesa). Validar aqui
 * antes serve para devolver mensagens claras (ex.: qual ativo ocupa o slot) em vez do erro 1644.
 */
@Service
public class RackService {

    private static final String ENTIDADE = "rack";
    public static final int CAPACIDADE_MAXIMA = 60;

    private final RackDAO dao;
    private final FileiraDAO fileiraDAO;
    private final AtivoDAO ativoDAO;
    private final Auditoria auditoria;

    public RackService(RackDAO dao, FileiraDAO fileiraDAO, AtivoDAO ativoDAO, Auditoria auditoria) {
        this.dao = dao;
        this.fileiraDAO = fileiraDAO;
        this.ativoDAO = ativoDAO;
        this.auditoria = auditoria;
    }

    public List<Rack> listar(Integer fileiraId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, fileiraId);
        }
    }

    public Rack buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Rack criar(Rack rack, int usuarioId) throws SQLException {
        validar(rack);
        return Transacao.executar(con -> {
            conferirFileira(con, rack.getFileiraId());
            rack.setId(dao.inserir(con, rack));
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, rack.getId(), null, rack);
            return rack;
        });
    }

    /** Regra: a capacidade não pode ficar menor que o maior slot já ocupado no rack. */
    public Rack atualizar(int id, Rack rack, int usuarioId) throws SQLException {
        validar(rack);
        return Transacao.executar(con -> {
            Rack anterior = dao.buscarPorIdParaAtualizar(con, id);
            if (anterior == null) {
                throw new NaoEncontradoException("Rack " + id + " não encontrado");
            }
            conferirFileira(con, rack.getFileiraId());
            Integer maiorSlot = ativoDAO.maiorSlotOcupado(con, id);
            if (maiorSlot != null && rack.getTotalSlots() < maiorSlot) {
                throw new ConflitoException("O rack tem ativo ocupando até o slot " + maiorSlot
                        + "; a capacidade não pode ser reduzida para " + rack.getTotalSlots() + " U");
            }
            rack.setId(id);
            dao.atualizar(con, rack);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, rack);
            return rack;
        });
    }

    /** Racks com ativos, aluguéis ou movimentações não podem ser excluídos (FK -> 409). */
    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            Rack anterior = obter(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    /** Resumo de ocupação de todos os racks (ou só os de uma sala), vindo da view View_ocupacao_rack. */
    public List<ResumoOcupacaoRack> ocupacaoGeral(Integer salaId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listarOcupacao(con, salaId);
        }
    }

    /**
     * Mapa de ocupação: percorre os ativos do rack em ordem de slot e anota os "buracos" livres
     * entre eles.
     */
    public OcupacaoRackResponse ocupacao(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            Rack rack = obter(con, id);
            List<Ativo> ativos = ativoDAO.listarPorRack(con, id);

            List<SlotOcupado> ocupados = new ArrayList<>();
            List<IntervaloLivre> livres = new ArrayList<>();
            int proximoLivre = 1;
            int totalOcupado = 0;
            for (Ativo a : ativos) {
                if (a.getSlotInicio() > proximoLivre) {
                    livres.add(intervalo(proximoLivre, a.getSlotInicio() - 1));
                }
                int tamanho = a.getSlotFim() - a.getSlotInicio() + 1;
                ocupados.add(new SlotOcupado(a.getId(), a.getNome(), a.getStatus(), a.getSlotInicio(), a.getSlotFim(), tamanho));
                totalOcupado += tamanho;
                proximoLivre = a.getSlotFim() + 1;
            }
            if (proximoLivre <= rack.getTotalSlots()) {
                livres.add(intervalo(proximoLivre, rack.getTotalSlots()));
            }

            double percentual = Math.round(totalOcupado * 1000.0 / rack.getTotalSlots()) / 10.0;
            return new OcupacaoRackResponse(rack.getId(), rack.getNome(), rack.getTotalSlots(), totalOcupado,
                    rack.getTotalSlots() - totalOcupado, percentual, ocupados, livres);
        }
    }

    /**
     * Valida se um ativo pode ocupar [slotInicio, slotFim] no rack. Usado no cadastro do ativo e
     * na movimentação. Precisa ser chamado DENTRO de uma transação: o rack é travado com
     * SELECT ... FOR UPDATE até o commit, evitando que duas operações simultâneas ocupem o mesmo slot.
     */
    public void validarPosicao(Connection con, Integer rackId, Integer slotInicio, Integer slotFim,
                               Integer ignorarAtivoId) throws SQLException {
        if (rackId == null || slotInicio == null || slotFim == null) {
            throw new NegocioException("Informe rack, slot inicial e slot final juntos");
        }
        if (slotInicio < 1 || slotFim < slotInicio) {
            throw new NegocioException("Slots inválidos: o inicial deve ser >= 1 e o final >= inicial");
        }
        Rack rack = dao.buscarPorIdParaAtualizar(con, rackId);
        if (rack == null) {
            throw new NegocioException("Rack " + rackId + " não existe");
        }
        if (slotFim > rack.getTotalSlots()) {
            throw new NegocioException("Slot final (" + slotFim + ") excede a capacidade do rack "
                    + rack.getNome() + " (" + rack.getTotalSlots() + " U)");
        }
        Ativo conflito = ativoDAO.buscarConflitoDeSlots(con, rackId, slotInicio, slotFim, ignorarAtivoId);
        if (conflito != null) {
            throw new ConflitoException("Slots " + slotInicio + "-" + slotFim + " do rack " + rack.getNome()
                    + " já estão ocupados pelo ativo '" + conflito.getNome() + "' (id " + conflito.getId()
                    + ", slots " + conflito.getSlotInicio() + "-" + conflito.getSlotFim() + ")");
        }
    }

    Rack obter(Connection con, int id) throws SQLException {
        Rack rack = dao.buscarPorId(con, id);
        if (rack == null) {
            throw new NaoEncontradoException("Rack " + id + " não encontrado");
        }
        return rack;
    }

    private void conferirFileira(Connection con, int fileiraId) throws SQLException {
        if (fileiraDAO.buscarPorId(con, fileiraId) == null) {
            throw new NegocioException("Fileira " + fileiraId + " não existe");
        }
    }

    private void validar(Rack rack) {
        rack.setNome(texto(rack.getNome(), "nome", 128));
        int total = obrigatorio(rack.getTotalSlots(), "totalSlots");
        if (total < 1 || total > CAPACIDADE_MAXIMA) {
            throw new NegocioException("totalSlots deve estar entre 1 e " + CAPACIDADE_MAXIMA);
        }
        obrigatorio(rack.getFileiraId(), "fileiraId");
    }

    private static IntervaloLivre intervalo(int inicio, int fim) {
        return new IntervaloLivre(inicio, fim, fim - inicio + 1);
    }
}
