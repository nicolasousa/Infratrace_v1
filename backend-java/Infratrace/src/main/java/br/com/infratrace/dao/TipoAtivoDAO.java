package br.com.infratrace.dao;

import br.com.infratrace.model.TipoAtivo;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL da tabela tipo_ativo.
 *
 * Padrão usado em todos os DAOs:
 *  - recebem a Connection pronta (quem abre, fecha e decide commit/rollback é o Service);
 *  - PreparedStatement e ResultSet são fechados com try-with-resources;
 *  - nenhuma regra de negócio aqui, só SQL e conversão ResultSet -> objeto.
 */
@Repository
public class TipoAtivoDAO {

    private static final String COLUNAS = "SELECT tpa_id, tpa_nome, tpa_descricao FROM tipo_ativo";

    public List<TipoAtivo> listar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " ORDER BY tpa_nome");
             ResultSet rs = ps.executeQuery()) {
            List<TipoAtivo> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    public TipoAtivo buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE tpa_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Insere e devolve o id gerado pelo AUTO_INCREMENT. */
    public int inserir(Connection con, TipoAtivo t) throws SQLException {
        String sql = "INSERT INTO tipo_ativo (tpa_nome, tpa_descricao) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getNome());
            ps.setString(2, t.getDescricao());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, TipoAtivo t) throws SQLException {
        String sql = "UPDATE tipo_ativo SET tpa_nome = ?, tpa_descricao = ? WHERE tpa_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, t.getNome());
            ps.setString(2, t.getDescricao());
            ps.setInt(3, t.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM tipo_ativo WHERE tpa_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private TipoAtivo mapear(ResultSet rs) throws SQLException {
        TipoAtivo t = new TipoAtivo();
        t.setId(rs.getInt("tpa_id"));
        t.setNome(rs.getString("tpa_nome"));
        t.setDescricao(rs.getString("tpa_descricao"));
        return t;
    }
}
