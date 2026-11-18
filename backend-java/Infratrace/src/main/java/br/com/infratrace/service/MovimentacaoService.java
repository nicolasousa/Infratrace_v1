package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.dao.AtivoDAO;
import br.com.infratrace.dao.MovimentacaoDAO;
import br.com.infratrace.dto.MovimentacaoRequest;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Ativo;
import br.com.infratrace.model.Movimentacao;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import static br.com.infratrace.service.Validacoes.obrigatorio;
import static br.com.infratrace.service.Validacoes.textoOpcional;

/**
 * Movimentação física de ativos: a principal TRANSAÇÃO MANUAL do projeto.
 *
 * A tabela movimentacao não altera o ativo sozinha. Por isso, na MESMA transação:
 *   1. trava o ativo (SELECT ... FOR UPDATE) e lê a posição atual (= origem);
 *   2. valida o destino (capacidade e slots livres, travando também o rack de destino);
 *   3. INSERT INTO movimentacao;
 *   4. UPDATE ativo SET fk_rac_id, atv_slot_inicio, atv_slot_fim;
 *   5. INSERT INTO log_auditoria;
 *   6. commit() — ou rollback() se QUALQUER passo falhar.
 *
 * Sem a transação, uma falha no passo 4 deixaria uma movimentação registrada para um ativo que
 * não saiu do lugar. Com ela, ou tudo acontece, ou nada acontece (atomicidade).
 *
 * Este Service está escrito "por extenso" (sem a classe Transacao) de propósito, para mostrar
 * cada chamada JDBC.
 */
@Service
public class MovimentacaoService {

    private final MovimentacaoDAO dao;
    private final AtivoDAO ativoDAO;
    private final RackService rackService;
    private final Auditoria auditoria;

    public MovimentacaoService(MovimentacaoDAO dao, AtivoDAO ativoDAO, RackService rackService, Auditoria auditoria) {
        this.dao = dao;
        this.ativoDAO = ativoDAO;
        this.rackService = rackService;
        this.auditoria = auditoria;
    }

    public List<Movimentacao> listar(Integer ativoId) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, ativoId);
        }
    }

    public Movimentacao buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            Movimentacao m = dao.buscarPorId(con, id);
            if (m == null) {
                throw new NaoEncontradoException("Movimentação " + id + " não encontrada");
            }
            return m;
        }
    }

    public Movimentacao movimentar(MovimentacaoRequest pedido, int usuarioId) throws SQLException {
        int ativoId = obrigatorio(pedido.ativoId(), "ativoId");
        String motivo = textoOpcional(pedido.motivo(), "motivo", 512);
        boolean paraEstoque = pedido.rackDestinoId() == null;
        if (paraEstoque && (pedido.slotInicioDestino() != null || pedido.slotFimDestino() != null)) {
            throw new NegocioException("Para mandar o ativo ao estoque, não informe slots de destino");
        }

        try (Connection con = Conexao.getConexao()) {
            con.setAutoCommit(false);                     // início da transação
            try {
                // 1. Trava o ativo: outra movimentação do mesmo ativo espera esta terminar.
                Ativo ativo = ativoDAO.buscarPorIdParaAtualizar(con, ativoId);
                if (ativo == null) {
                    throw new NaoEncontradoException("Ativo " + ativoId + " não encontrado");
                }
                if (AtivoService.DESCARTADO.equals(ativo.getStatus())) {
                    throw new ConflitoException("Ativo descartado não pode ser movimentado");
                }
                if (paraEstoque && ativo.getRackId() == null) {
                    throw new ConflitoException("O ativo já está no estoque");
                }
                if (!paraEstoque
                        && Objects.equals(ativo.getRackId(), pedido.rackDestinoId())
                        && Objects.equals(ativo.getSlotInicio(), pedido.slotInicioDestino())
                        && Objects.equals(ativo.getSlotFim(), pedido.slotFimDestino())) {
                    throw new ConflitoException("O ativo já está nessa posição");
                }

                // 2. Destino válido? (ignora o próprio ativo: ele pode "deslizar" no mesmo rack)
                if (!paraEstoque) {
                    rackService.validarPosicao(con, pedido.rackDestinoId(), pedido.slotInicioDestino(),
                            pedido.slotFimDestino(), ativoId);
                }

                // 3. Registra a movimentação. A origem vem do banco, não do cliente.
                Movimentacao mov = new Movimentacao();
                mov.setMotivo(motivo);
                mov.setRackOrigemId(ativo.getRackId());
                mov.setSlotInicioOrigem(ativo.getSlotInicio());
                mov.setSlotFimOrigem(ativo.getSlotFim());
                mov.setRackDestinoId(pedido.rackDestinoId());
                mov.setSlotInicioDestino(pedido.slotInicioDestino());
                mov.setSlotFimDestino(pedido.slotFimDestino());
                mov.setAtivoId(ativoId);
                mov.setUsuarioId(usuarioId);
                int movId = dao.inserir(con, mov);

                // 4. Atualiza a posição do ativo.
                ativoDAO.atualizarLocalizacao(con, ativoId, pedido.rackDestinoId(),
                        pedido.slotInicioDestino(), pedido.slotFimDestino());

                // 5. Auditoria (mesma conexão = mesma transação).
                Ativo depois = ativoDAO.buscarPorId(con, ativoId);
                Movimentacao salva = dao.buscarPorId(con, movId);
                auditoria.registrar(con, usuarioId, Auditoria.INSERT, "movimentacao", movId, null, salva);
                auditoria.registrar(con, usuarioId, Auditoria.UPDATE, "ativo", ativoId, ativo, depois);

                // 6. Tudo certo: confirma.
                con.commit();
                return salva;
            } catch (SQLException | RuntimeException e) {
                con.rollback();                           // desfaz os passos 3, 4 e 5
                throw e;
            }
        }
    }
}
