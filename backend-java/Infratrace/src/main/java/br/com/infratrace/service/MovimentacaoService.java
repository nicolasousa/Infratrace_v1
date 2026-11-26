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
 * Movimentação física de ativos. A TRANSAÇÃO é feita pelo MySQL, na procedure
 * Procedure_movimentar_ativo (database/infratrace_mysql.sql).
 *
 * Divisão de trabalho:
 *   - Java (este Service): confere as condições lendo o banco, para devolver mensagens claras
 *     e o código HTTP certo (404, 400, 409). Não abre transação.
 *   - MySQL (procedure): START TRANSACTION → trava ativo e rack (FOR UPDATE) → INSERT
 *     movimentacao → UPDATE ativo → INSERT log_auditoria → COMMIT, ou ROLLBACK se QUALQUER
 *     passo falhar (EXIT HANDLER). Os triggers conferem capacidade e slots de novo.
 *
 * Sem a transação, uma falha no UPDATE deixaria uma movimentação registrada para um ativo que
 * não saiu do lugar. Com ela, ou tudo acontece, ou nada acontece (atomicidade).
 *
 * As demais escritas do sistema continuam com a transação no Java (ver config/Transacao).
 */
@Service
public class MovimentacaoService {

    private final MovimentacaoDAO dao;
    private final AtivoDAO ativoDAO;
    private final RackService rackService;

    public MovimentacaoService(MovimentacaoDAO dao, AtivoDAO ativoDAO, RackService rackService) {
        this.dao = dao;
        this.ativoDAO = ativoDAO;
        this.rackService = rackService;
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
            // 1. Condições conferidas no Java (só leitura), para mensagens claras.
            Ativo ativo = ativoDAO.buscarPorId(con, ativoId);
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
            // Destino válido? (ignora o próprio ativo: ele pode "deslizar" no mesmo rack)
            if (!paraEstoque) {
                rackService.validarPosicao(con, pedido.rackDestinoId(), pedido.slotInicioDestino(),
                        pedido.slotFimDestino(), ativoId);
            }

            // 2. A transação: a procedure grava tudo ou nada. Se ela der erro (ex.: 1644 de um
            //    trigger), o MySQL já fez o ROLLBACK e a SQLException vai para o TratadorDeErros.
            int movId = dao.movimentar(con, ativoId, pedido.rackDestinoId(), pedido.slotInicioDestino(),
                    pedido.slotFimDestino(), motivo, usuarioId);

            return dao.buscarPorId(con, movId);
        }
    }
}
