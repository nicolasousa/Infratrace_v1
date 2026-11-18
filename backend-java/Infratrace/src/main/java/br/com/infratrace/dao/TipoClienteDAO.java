package br.com.infratrace.dao;

import br.com.infratrace.model.TipoCliente;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela tipo_cliente. */
@Repository
public class TipoClienteDAO {

    private static final String COLUNAS = "SELECT tpc_id, tpc_nome FROM tipo_cliente";

    public List<TipoCliente> listar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " ORDER BY tpc_nome");
             ResultSet rs = ps.executeQuery()) {
            List<TipoCliente> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    public TipoCliente buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE tpc_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, TipoCliente t) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO tipo_cliente (tpc_nome) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getNome());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, TipoCliente t) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE tipo_cliente SET tpc_nome = ? WHERE tpc_id = ?")) {
            ps.setString(1, t.getNome());
            ps.setInt(2, t.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM tipo_cliente WHERE tpc_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private TipoCliente mapear(ResultSet rs) throws SQLException {
        TipoCliente t = new TipoCliente();
        t.setId(rs.getInt("tpc_id"));
        t.setNome(rs.getString("tpc_nome"));
        return t;
    }
}
