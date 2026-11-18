package br.com.infratrace.dao;

import br.com.infratrace.model.Fileira;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela fileira. */
@Repository
public class FileiraDAO {

    private static final String COLUNAS = "SELECT fil_id, fil_nome, fk_sal_id FROM fileira";

    /** Lista todas as fileiras ou só as de uma sala (salaId != null). */
    public List<Fileira> listar(Connection con, Integer salaId) throws SQLException {
        String sql = COLUNAS + (salaId != null ? " WHERE fk_sal_id = ?" : "") + " ORDER BY fk_sal_id, fil_nome";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (salaId != null) {
                ps.setInt(1, salaId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Fileira> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public Fileira buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE fil_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, Fileira f) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO fileira (fil_nome, fk_sal_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.getNome());
            ps.setInt(2, f.getSalaId());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Fileira f) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE fileira SET fil_nome = ?, fk_sal_id = ? WHERE fil_id = ?")) {
            ps.setString(1, f.getNome());
            ps.setInt(2, f.getSalaId());
            ps.setInt(3, f.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM fileira WHERE fil_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Fileira mapear(ResultSet rs) throws SQLException {
        Fileira f = new Fileira();
        f.setId(rs.getInt("fil_id"));
        f.setNome(rs.getString("fil_nome"));
        f.setSalaId(rs.getInt("fk_sal_id"));
        return f;
    }
}
