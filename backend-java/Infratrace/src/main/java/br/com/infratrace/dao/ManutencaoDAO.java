package br.com.infratrace.dao;

import br.com.infratrace.model.Manutencao;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela manutencao. */
@Repository
public class ManutencaoDAO {

    private static final String COLUNAS = "SELECT man_id, man_tipo, man_descricao, man_responsavel, "
            + "man_data_inicio, man_data_fim, fk_atv_id, fk_usr_id FROM manutencao";

    public List<Manutencao> listar(Connection con, Integer ativoId) throws SQLException {
        String sql = COLUNAS + (ativoId != null ? " WHERE fk_atv_id = ?" : "") + " ORDER BY man_data_inicio DESC, man_id DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (ativoId != null) {
                ps.setInt(1, ativoId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Manutencao> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public Manutencao buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE man_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Quantas manutenções em andamento (sem data de fim) o ativo tem, sem contar a informada. */
    public int contarEmAndamento(Connection con, int ativoId, Integer ignorarId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM manutencao WHERE fk_atv_id = ? AND man_data_fim IS NULL"
                + " AND (? IS NULL OR man_id <> ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ativoId);
            ps.setObject(2, ignorarId);
            ps.setObject(3, ignorarId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int inserir(Connection con, Manutencao m) throws SQLException {
        String sql = "INSERT INTO manutencao (man_tipo, man_descricao, man_responsavel, man_data_inicio, "
                + "man_data_fim, fk_atv_id, fk_usr_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getTipo());
            ps.setString(2, m.getDescricao());
            ps.setString(3, m.getResponsavel());
            ps.setObject(4, m.getDataInicio());
            ps.setObject(5, m.getDataFim());
            ps.setInt(6, m.getAtivoId());
            ps.setInt(7, m.getUsuarioId());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Manutencao m) throws SQLException {
        String sql = "UPDATE manutencao SET man_tipo = ?, man_descricao = ?, man_responsavel = ?, "
                + "man_data_inicio = ?, man_data_fim = ? WHERE man_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getTipo());
            ps.setString(2, m.getDescricao());
            ps.setString(3, m.getResponsavel());
            ps.setObject(4, m.getDataInicio());
            ps.setObject(5, m.getDataFim());
            ps.setInt(6, m.getId());
            ps.executeUpdate();
        }
    }

    private Manutencao mapear(ResultSet rs) throws SQLException {
        Manutencao m = new Manutencao();
        m.setId(rs.getInt("man_id"));
        m.setTipo(rs.getString("man_tipo"));
        m.setDescricao(rs.getString("man_descricao"));
        m.setResponsavel(rs.getString("man_responsavel"));
        m.setDataInicio(rs.getObject("man_data_inicio", LocalDate.class));
        m.setDataFim(rs.getObject("man_data_fim", LocalDate.class));
        m.setAtivoId(rs.getInt("fk_atv_id"));
        m.setUsuarioId(rs.getInt("fk_usr_id"));
        return m;
    }
}
