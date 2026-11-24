package br.com.infratrace.dao;

import br.com.infratrace.model.Movimentacao;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela movimentacao. Movimentações são histórico: não há UPDATE nem DELETE. */
@Repository
public class MovimentacaoDAO {

    private static final String COLUNAS = "SELECT mov_id, mov_data, mov_motivo, fk_rac_origem_id, "
            + "mov_slot_inicio_origem, mov_slot_fim_origem, fk_rac_destino_id, mov_slot_inicio_destino, "
            + "mov_slot_fim_destino, fk_atv_id, fk_usr_id FROM movimentacao";

    public List<Movimentacao> listar(Connection con, Integer ativoId) throws SQLException {
        String sql = COLUNAS + (ativoId != null ? " WHERE fk_atv_id = ?" : "") + " ORDER BY mov_data DESC, mov_id DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (ativoId != null) {
                ps.setInt(1, ativoId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Movimentacao> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public Movimentacao buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE mov_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Chama a procedure Procedure_movimentar_ativo, que faz a TRANSAÇÃO inteira no MySQL
     * (INSERT movimentacao + UPDATE ativo + auditoria, com COMMIT ou ROLLBACK).
     *
     * CallableStatement é o tipo do JDBC para chamar procedures. Os "?" são os parâmetros na ordem
     * da procedure; o último é de saída (OUT) e devolve o id da movimentação criada.
     *
     * A conexão precisa estar em autocommit (o padrão): quem abre e fecha a transação é a
     * procedure, com START TRANSACTION e COMMIT/ROLLBACK.
     */
    public int movimentar(Connection con, int ativoId, Integer rackDestinoId, Integer slotInicio,
                          Integer slotFim, String motivo, int usuarioId) throws SQLException {
        try (CallableStatement cs = con.prepareCall("{CALL Procedure_movimentar_ativo(?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, ativoId);
            cs.setObject(2, rackDestinoId, Types.INTEGER);   // null = estoque
            cs.setObject(3, slotInicio, Types.INTEGER);
            cs.setObject(4, slotFim, Types.INTEGER);
            cs.setString(5, motivo);
            cs.setInt(6, usuarioId);
            cs.registerOutParameter(7, Types.INTEGER);        // p_mov_id (OUT)
            cs.execute();
            return cs.getInt(7);
        }
    }

    /**
     * INSERT direto, usado por outras operações que geram movimentação dentro da própria
     * transação JDBC (ex.: cadastro de ativo já instalado, descarte).
     * mov_data fica com o DEFAULT CURRENT_TIMESTAMP do banco.
     */
    public int inserir(Connection con, Movimentacao m) throws SQLException {
        String sql = "INSERT INTO movimentacao (mov_motivo, fk_rac_origem_id, mov_slot_inicio_origem, "
                + "mov_slot_fim_origem, fk_rac_destino_id, mov_slot_inicio_destino, mov_slot_fim_destino, "
                + "fk_atv_id, fk_usr_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getMotivo());
            ps.setObject(2, m.getRackOrigemId());
            ps.setObject(3, m.getSlotInicioOrigem());
            ps.setObject(4, m.getSlotFimOrigem());
            ps.setObject(5, m.getRackDestinoId());
            ps.setObject(6, m.getSlotInicioDestino());
            ps.setObject(7, m.getSlotFimDestino());
            ps.setInt(8, m.getAtivoId());
            ps.setInt(9, m.getUsuarioId());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    private Movimentacao mapear(ResultSet rs) throws SQLException {
        Movimentacao m = new Movimentacao();
        m.setId(rs.getInt("mov_id"));
        m.setData(rs.getObject("mov_data", LocalDateTime.class));
        m.setMotivo(rs.getString("mov_motivo"));
        m.setRackOrigemId(rs.getObject("fk_rac_origem_id", Integer.class));
        m.setSlotInicioOrigem(rs.getObject("mov_slot_inicio_origem", Integer.class));
        m.setSlotFimOrigem(rs.getObject("mov_slot_fim_origem", Integer.class));
        m.setRackDestinoId(rs.getObject("fk_rac_destino_id", Integer.class));
        m.setSlotInicioDestino(rs.getObject("mov_slot_inicio_destino", Integer.class));
        m.setSlotFimDestino(rs.getObject("mov_slot_fim_destino", Integer.class));
        m.setAtivoId(rs.getInt("fk_atv_id"));
        m.setUsuarioId(rs.getInt("fk_usr_id"));
        return m;
    }
}
