package br.com.infratrace.dao;

import br.com.infratrace.dto.ResumoOcupacaoRack;
import br.com.infratrace.model.Rack;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela rack. */
@Repository
public class RackDAO {

    private static final String COLUNAS = "SELECT rac_id, rac_nome, rac_total_slots, fk_fil_id FROM rack";

    public List<Rack> listar(Connection con, Integer fileiraId) throws SQLException {
        String sql = COLUNAS + (fileiraId != null ? " WHERE fk_fil_id = ?" : "") + " ORDER BY fk_fil_id, rac_nome";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (fileiraId != null) {
                ps.setInt(1, fileiraId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Rack> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /**
     * Resumo de ocupação de todos os racks, lido da VIEW View_ocupacao_rack. O JOIN com fileira e
     * sala, o COUNT/SUM dos slots e o percentual ficam na view; aqui é só um SELECT simples.
     */
    public List<ResumoOcupacaoRack> listarOcupacao(Connection con, Integer salaId) throws SQLException {
        String sql = "SELECT rac_id, rac_nome, fil_id, fil_nome, sal_id, sal_nome, rac_total_slots, qtd_ativos, "
                + "slots_ocupados, slots_livres, percentual_ocupacao FROM View_ocupacao_rack"
                + (salaId != null ? " WHERE sal_id = ?" : "")
                + " ORDER BY sal_nome, fil_nome, rac_nome";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (salaId != null) {
                ps.setInt(1, salaId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ResumoOcupacaoRack> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new ResumoOcupacaoRack(rs.getInt("rac_id"), rs.getString("rac_nome"),
                            rs.getInt("fil_id"), rs.getString("fil_nome"), rs.getInt("sal_id"),
                            rs.getString("sal_nome"), rs.getInt("rac_total_slots"), rs.getInt("qtd_ativos"),
                            rs.getInt("slots_ocupados"), rs.getInt("slots_livres"),
                            rs.getDouble("percentual_ocupacao")));
                }
                return lista;
            }
        }
    }

    public Rack buscarPorId(Connection con, int id) throws SQLException {
        return buscar(con, id, "");
    }

    /**
     * SELECT ... FOR UPDATE: lê o rack e TRAVA a linha até o commit/rollback da transação.
     * Assim, duas pessoas instalando ativos no mesmo rack ao mesmo tempo são atendidas uma de
     * cada vez, e a segunda já enxerga os slots ocupados pela primeira. Só tem efeito com
     * auto-commit desligado (dentro de uma transação).
     */
    public Rack buscarPorIdParaAtualizar(Connection con, int id) throws SQLException {
        return buscar(con, id, " FOR UPDATE");
    }

    private Rack buscar(Connection con, int id, String trava) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE rac_id = ?" + trava)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, Rack r) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO rack (rac_nome, rac_total_slots, fk_fil_id) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getNome());
            ps.setInt(2, r.getTotalSlots());
            ps.setInt(3, r.getFileiraId());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Rack r) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE rack SET rac_nome = ?, rac_total_slots = ?, fk_fil_id = ? WHERE rac_id = ?")) {
            ps.setString(1, r.getNome());
            ps.setInt(2, r.getTotalSlots());
            ps.setInt(3, r.getFileiraId());
            ps.setInt(4, r.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM rack WHERE rac_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Rack mapear(ResultSet rs) throws SQLException {
        Rack r = new Rack();
        r.setId(rs.getInt("rac_id"));
        r.setNome(rs.getString("rac_nome"));
        r.setTotalSlots(rs.getInt("rac_total_slots"));
        r.setFileiraId(rs.getInt("fk_fil_id"));
        return r;
    }
}
