package br.com.infratrace.dao;

import br.com.infratrace.model.Cliente;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela cliente. Clientes não são apagados: são inativados (cli_ativo = FALSE). */
@Repository
public class ClienteDAO {

    private static final String COLUNAS = "SELECT cli_id, cli_nome, cli_cnpj, cli_representante, cli_ativo, fk_tpc_id FROM cliente";

    public List<Cliente> listar(Connection con, Boolean ativo) throws SQLException {
        String sql = COLUNAS + (ativo != null ? " WHERE cli_ativo = ?" : "") + " ORDER BY cli_nome";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (ativo != null) {
                ps.setBoolean(1, ativo);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Cliente> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public Cliente buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE cli_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Cliente buscarPorCnpj(Connection con, String cnpj) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE cli_cnpj = ?")) {
            ps.setString(1, cnpj);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, Cliente c) throws SQLException {
        String sql = "INSERT INTO cliente (cli_nome, cli_cnpj, cli_representante, cli_ativo, fk_tpc_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNome());
            ps.setString(2, c.getCnpj());
            ps.setString(3, c.getRepresentante());
            ps.setBoolean(4, c.getAtivo());
            ps.setInt(5, c.getTipoClienteId());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Cliente c) throws SQLException {
        String sql = "UPDATE cliente SET cli_nome = ?, cli_cnpj = ?, cli_representante = ?, cli_ativo = ?, "
                + "fk_tpc_id = ? WHERE cli_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.getNome());
            ps.setString(2, c.getCnpj());
            ps.setString(3, c.getRepresentante());
            ps.setBoolean(4, c.getAtivo());
            ps.setInt(5, c.getTipoClienteId());
            ps.setInt(6, c.getId());
            ps.executeUpdate();
        }
    }

    private Cliente mapear(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setId(rs.getInt("cli_id"));
        c.setNome(rs.getString("cli_nome"));
        c.setCnpj(rs.getString("cli_cnpj"));
        c.setRepresentante(rs.getString("cli_representante"));
        c.setAtivo(rs.getBoolean("cli_ativo"));
        c.setTipoClienteId(rs.getInt("fk_tpc_id"));
        return c;
    }
}
