package br.com.infratrace.dao;

import br.com.infratrace.model.Aluguel;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela aluguel. */
@Repository
public class AluguelDAO {

    private static final String COLUNAS = "SELECT alu_id, alu_data_inicio, alu_data_fim, alu_valor, fk_cli_id, fk_rac_id FROM aluguel";

    public List<Aluguel> listar(Connection con, Integer clienteId, Integer rackId) throws SQLException {
        StringBuilder sql = new StringBuilder(COLUNAS).append(" WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();
        if (clienteId != null) {
            sql.append(" AND fk_cli_id = ?");
            parametros.add(clienteId);
        }
        if (rackId != null) {
            sql.append(" AND fk_rac_id = ?");
            parametros.add(rackId);
        }
        sql.append(" ORDER BY alu_data_inicio DESC");
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            return listarResultado(ps);
        }
    }

    /** Aluguéis cujo contrato termina entre as duas datas. */
    public List<Aluguel> listarVencendo(Connection con, LocalDate de, LocalDate ate) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE alu_data_fim BETWEEN ? AND ? ORDER BY alu_data_fim")) {
            ps.setObject(1, de);
            ps.setObject(2, ate);
            return listarResultado(ps);
        }
    }

    public Aluguel buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE alu_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Primeiro aluguel do rack com período sobreposto a [inicio, fim], ou null. */
    public Aluguel buscarSobreposicao(Connection con, int rackId, LocalDate inicio, LocalDate fim, Integer ignorarId)
            throws SQLException {
        String sql = COLUNAS + " WHERE fk_rac_id = ? AND alu_data_inicio <= ? AND alu_data_fim >= ?"
                + " AND (? IS NULL OR alu_id <> ?) ORDER BY alu_data_inicio LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, rackId);
            ps.setObject(2, fim);
            ps.setObject(3, inicio);
            ps.setObject(4, ignorarId);
            ps.setObject(5, ignorarId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, Aluguel a) throws SQLException {
        String sql = "INSERT INTO aluguel (alu_data_inicio, alu_data_fim, alu_valor, fk_cli_id, fk_rac_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, a);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Aluguel a) throws SQLException {
        String sql = "UPDATE aluguel SET alu_data_inicio = ?, alu_data_fim = ?, alu_valor = ?, fk_cli_id = ?, "
                + "fk_rac_id = ? WHERE alu_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            preencher(ps, a);
            ps.setInt(6, a.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM aluguel WHERE alu_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private void preencher(PreparedStatement ps, Aluguel a) throws SQLException {
        ps.setObject(1, a.getDataInicio());
        ps.setObject(2, a.getDataFim());
        ps.setBigDecimal(3, a.getValor());
        ps.setInt(4, a.getClienteId());
        ps.setInt(5, a.getRackId());
    }

    private List<Aluguel> listarResultado(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Aluguel> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    private Aluguel mapear(ResultSet rs) throws SQLException {
        Aluguel a = new Aluguel();
        a.setId(rs.getInt("alu_id"));
        a.setDataInicio(rs.getObject("alu_data_inicio", LocalDate.class));
        a.setDataFim(rs.getObject("alu_data_fim", LocalDate.class));
        a.setValor(rs.getObject("alu_valor", BigDecimal.class));
        a.setClienteId(rs.getInt("fk_cli_id"));
        a.setRackId(rs.getInt("fk_rac_id"));
        return a;
    }
}
