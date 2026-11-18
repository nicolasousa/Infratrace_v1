package br.com.infratrace.dao;

import br.com.infratrace.model.Usuario;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela usuario. A senha nunca é gravada: só o hash dela (usr_senha_hash). */
@Repository
public class UsuarioDAO {

    private static final String COLUNAS = "SELECT usr_id, usr_nome, usr_email, usr_senha_hash, usr_perfil, usr_ativo, "
            + "usr_token_recuperacao, usr_token_expiracao, usr_criado_em FROM usuario";

    public List<Usuario> listar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " ORDER BY usr_nome");
             ResultSet rs = ps.executeQuery()) {
            List<Usuario> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    public Usuario buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE usr_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Usuario buscarPorEmail(Connection con, String email) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE usr_email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int contar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM usuario");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Quantos ADMIN ativos existem, sem contar o usuário informado. */
    public int contarAdminsAtivos(Connection con, int ignorarId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuario WHERE usr_perfil = 'ADMIN' AND usr_ativo = TRUE AND usr_id <> ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ignorarId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int inserir(Connection con, Usuario u) throws SQLException {
        String sql = "INSERT INTO usuario (usr_nome, usr_email, usr_senha_hash, usr_perfil, usr_ativo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNome());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getSenhaHash());
            ps.setString(4, u.getPerfil());
            ps.setBoolean(5, u.getAtivo());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, Usuario u) throws SQLException {
        String sql = "UPDATE usuario SET usr_nome = ?, usr_email = ?, usr_senha_hash = ?, usr_perfil = ?, usr_ativo = ? "
                + "WHERE usr_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, u.getNome());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getSenhaHash());
            ps.setString(4, u.getPerfil());
            ps.setBoolean(5, u.getAtivo());
            ps.setInt(6, u.getId());
            ps.executeUpdate();
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setId(rs.getInt("usr_id"));
        u.setNome(rs.getString("usr_nome"));
        u.setEmail(rs.getString("usr_email"));
        u.setSenhaHash(rs.getString("usr_senha_hash"));
        u.setPerfil(rs.getString("usr_perfil"));
        u.setAtivo(rs.getBoolean("usr_ativo"));
        u.setTokenRecuperacao(rs.getString("usr_token_recuperacao"));
        u.setTokenExpiracao(rs.getObject("usr_token_expiracao", LocalDateTime.class));
        u.setCriadoEm(rs.getObject("usr_criado_em", LocalDateTime.class));
        return u;
    }
}
