package br.com.infratrace.dao;

import br.com.infratrace.model.Sala;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela sala. */
@Repository
public class SalaDAO {

    private static final String COLUNAS = "SELECT sal_id, sal_nome, sal_descricao FROM sala";

    public List<Sala> listar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " ORDER BY sal_nome");
             ResultSet rs = ps.executeQuery()) {
            List<Sala> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    public Sala buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE sal_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, Sala s) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO sala (sal_nome, sal_descricao) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getNome());
            ps.setString(2, s.getDescricao());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Sala s) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE sala SET sal_nome = ?, sal_descricao = ? WHERE sal_id = ?")) {
            ps.setString(1, s.getNome());
            ps.setString(2, s.getDescricao());
            ps.setInt(3, s.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM sala WHERE sal_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Sala mapear(ResultSet rs) throws SQLException {
        Sala s = new Sala();
        s.setId(rs.getInt("sal_id"));
        s.setNome(rs.getString("sal_nome"));
        s.setDescricao(rs.getString("sal_descricao"));
        return s;
    }
}
