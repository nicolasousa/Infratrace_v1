package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.AtivoDAO;
import br.com.infratrace.dao.MovimentacaoDAO;
import br.com.infratrace.dao.NotaFiscalDAO;
import br.com.infratrace.dao.TipoAtivoDAO;
import br.com.infratrace.dto.DescarteRequest;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Ativo;
import br.com.infratrace.model.Movimentacao;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static br.com.infratrace.service.Validacoes.opcao;
import static br.com.infratrace.service.Validacoes.texto;
import static br.com.infratrace.service.Validacoes.textoOpcional;

/**
 * Regras de ativo.
 *  - Cadastro: pode já nascer instalado num rack (gera uma movimentação "do estoque").
 *  - Alteração: só dados cadastrais e status. Mudar de lugar é sempre por POST /movimentacoes,
 *    para que todo deslocamento físico fique no histórico.
 *  - Descarte: status DESCARTADO, data e motivo; se estava em rack, sai dele (com movimentação).
 */
@Service
public class AtivoService {

    private static final String ENTIDADE = "ativo";
    public static final String DESCARTADO = "DESCARTADO";
    public static final Set<String> STATUS = Set.of("ATIVO", "INATIVO", "EM_MANUTENCAO", "AGUARDANDO_DESCARTE", DESCARTADO);
    private static final Set<String> STATUS_EDITAVEIS = Set.of("ATIVO", "INATIVO", "EM_MANUTENCAO", "AGUARDANDO_DESCARTE");

    private final AtivoDAO dao;
    private final TipoAtivoDAO tipoAtivoDAO;
    private final NotaFiscalDAO notaFiscalDAO;
    private final MovimentacaoDAO movimentacaoDAO;
    private final RackService rackService;
    private final Auditoria auditoria;

    public AtivoService(AtivoDAO dao, TipoAtivoDAO tipoAtivoDAO, NotaFiscalDAO notaFiscalDAO,
                        MovimentacaoDAO movimentacaoDAO, RackService rackService, Auditoria auditoria) {
        this.dao = dao;
        this.tipoAtivoDAO = tipoAtivoDAO;
        this.notaFiscalDAO = notaFiscalDAO;
        this.movimentacaoDAO = movimentacaoDAO;
        this.rackService = rackService;
        this.auditoria = auditoria;
    }

    public List<Ativo> listar(String status, Integer rackId) throws SQLException {
        String filtroStatus = status == null ? null : opcao(status, "status", STATUS);
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, filtroStatus, rackId);
        }
    }

    public Ativo buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, id);
        }
    }

    public Ativo criar(Ativo ativo, int usuarioId) throws SQLException {
        validarCadastro(ativo);
        ativo.setDataDescarte(null);
        ativo.setMotivoDescarte(null);
        boolean instalar = ativo.getRackId() != null || ativo.getSlotInicio() != null || ativo.getSlotFim() != null;

        return Transacao.executar(con -> {
            conferirReferencias(con, ativo);
            if (instalar) {
                rackService.validarPosicao(con, ativo.getRackId(), ativo.getSlotInicio(), ativo.getSlotFim(), null);
            }
            int id = dao.inserir(con, ativo);

            if (instalar) {
                Movimentacao mov = new Movimentacao();
                mov.setMotivo("Instalação no cadastro do ativo");
                mov.setRackDestinoId(ativo.getRackId());
                mov.setSlotInicioDestino(ativo.getSlotInicio());
                mov.setSlotFimDestino(ativo.getSlotFim());
                mov.setAtivoId(id);
                mov.setUsuarioId(usuarioId);
                movimentacaoDAO.inserir(con, mov);
            }

            Ativo salvo = dao.buscarPorId(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.INSERT, ENTIDADE, id, null, salvo);
            return salvo;
        });
    }

    /** Altera dados cadastrais e status. rackId e slots enviados no corpo são ignorados. */
    public Ativo atualizar(int id, Ativo ativo, int usuarioId) throws SQLException {
        validarCadastro(ativo);
        return Transacao.executar(con -> {
            Ativo anterior = obterParaAtualizar(con, id);
            if (DESCARTADO.equals(anterior.getStatus())) {
                throw new ConflitoException("Ativo descartado não pode ser alterado");
            }
            conferirReferencias(con, ativo);
            ativo.setId(id);
            dao.atualizarCadastro(con, ativo);
            Ativo atualizado = dao.buscarPorId(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, atualizado);
            return atualizado;
        });
    }

    public Ativo descartar(int id, DescarteRequest pedido, int usuarioId) throws SQLException {
        String motivo = texto(pedido.motivo(), "motivo", 512);
        LocalDate data = pedido.data() != null ? pedido.data() : LocalDate.now();
        if (data.isAfter(LocalDate.now())) {
            throw new NegocioException("A data de descarte não pode estar no futuro");
        }
        return Transacao.executar(con -> {
            Ativo anterior = obterParaAtualizar(con, id);
            if (DESCARTADO.equals(anterior.getStatus())) {
                throw new ConflitoException("O ativo já foi descartado em " + anterior.getDataDescarte());
            }
            if (anterior.getRackId() != null) {
                Movimentacao mov = new Movimentacao();
                mov.setMotivo("Retirada para descarte: " + motivo);
                mov.setRackOrigemId(anterior.getRackId());
                mov.setSlotInicioOrigem(anterior.getSlotInicio());
                mov.setSlotFimOrigem(anterior.getSlotFim());
                mov.setAtivoId(id);
                mov.setUsuarioId(usuarioId);
                movimentacaoDAO.inserir(con, mov);
            }
            dao.registrarDescarte(con, id, data, motivo);
            Ativo atualizado = dao.buscarPorId(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, ENTIDADE, id, anterior, atualizado);
            return atualizado;
        });
    }

    /**
     * Exclusão física: só funciona se o ativo não tiver histórico (movimentações, manutenções,
     * anexos). Caso tenha, o MySQL recusa pela FK e a API responde 409 — o caminho certo é o descarte.
     */
    public void excluir(int id, int usuarioId) throws SQLException {
        Transacao.executar(con -> {
            Ativo anterior = obterParaAtualizar(con, id);
            dao.excluir(con, id);
            auditoria.registrar(con, usuarioId, Auditoria.DELETE, ENTIDADE, id, anterior, null);
            return null;
        });
    }

    Ativo obter(Connection con, int id) throws SQLException {
        Ativo ativo = dao.buscarPorId(con, id);
        if (ativo == null) {
            throw new NaoEncontradoException("Ativo " + id + " não encontrado");
        }
        return ativo;
    }

    Ativo obterParaAtualizar(Connection con, int id) throws SQLException {
        Ativo ativo = dao.buscarPorIdParaAtualizar(con, id);
        if (ativo == null) {
            throw new NaoEncontradoException("Ativo " + id + " não encontrado");
        }
        return ativo;
    }

    private void conferirReferencias(Connection con, Ativo ativo) throws SQLException {
        if (tipoAtivoDAO.buscarPorId(con, ativo.getTipoAtivoId()) == null) {
            throw new NegocioException("Tipo de ativo " + ativo.getTipoAtivoId() + " não existe");
        }
        if (ativo.getNotaFiscalId() != null && notaFiscalDAO.buscarPorId(con, ativo.getNotaFiscalId()) == null) {
            throw new NegocioException("Nota fiscal " + ativo.getNotaFiscalId() + " não existe");
        }
    }

    private void validarCadastro(Ativo ativo) {
        ativo.setNome(texto(ativo.getNome(), "nome", 128));
        ativo.setFabricante(texto(ativo.getFabricante(), "fabricante", 128));
        ativo.setModelo(texto(ativo.getModelo(), "modelo", 128));
        ativo.setNumSerie(texto(ativo.getNumSerie(), "numSerie", 64));
        ativo.setPatrimonio(textoOpcional(ativo.getPatrimonio(), "patrimonio", 32));
        Validacoes.obrigatorio(ativo.getTipoAtivoId(), "tipoAtivoId");

        if (ativo.getStatus() == null) {
            ativo.setStatus("ATIVO");
        } else {
            String status = opcao(ativo.getStatus(), "status", STATUS);
            if (!STATUS_EDITAVEIS.contains(status)) {
                throw new NegocioException("Para descartar um ativo use POST /ativos/{id}/descarte");
            }
            ativo.setStatus(status);
        }
        if (ativo.getValorCompra() != null && ativo.getValorCompra().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("O valor de compra não pode ser negativo");
        }
        if (ativo.getDataAquisicao() != null && ativo.getDataAquisicao().isAfter(LocalDate.now())) {
            throw new NegocioException("A data de aquisição não pode estar no futuro");
        }
        if (ativo.getDataAquisicao() != null && ativo.getGarantia() != null
                && ativo.getGarantia().isBefore(ativo.getDataAquisicao())) {
            throw new NegocioException("O vencimento da garantia não pode ser anterior à aquisição");
        }
    }
}
