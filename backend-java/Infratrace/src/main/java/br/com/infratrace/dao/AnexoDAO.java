package br.com.infratrace.dao;

import br.com.infratrace.model.Anexo;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela anexo. Só os dados do arquivo; gravar e ler o arquivo é com o ArmazenamentoDeArquivos. */
@Repository
public class AnexoDAO {

    private static final String COLUNAS = "SELECT anx_id, anx_tipo, anx_nome_original, anx_caminho, anx_enviado_em, "
            + "fk_atv_id FROM anexo";

    public List<Anexo> listarPorAtivo(Connection con, int ativoId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE fk_atv_id = ? ORDER BY anx_enviado_em DESC, anx_id DESC")) {
            ps.setInt(1, ativoId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Anexo> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public Anexo buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE anx_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, Anexo a) throws SQLException {
        String sql = "INSERT INTO anexo (anx_tipo, anx_nome_original, anx_caminho, fk_atv_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.getTipo());
            ps.setString(2, a.getNomeOriginal());
            ps.setString(3, a.getCaminho());
            ps.setInt(4, a.getAtivoId());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM anexo WHERE anx_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Anexo mapear(ResultSet rs) throws SQLException {
        Anexo a = new Anexo();
        a.setId(rs.getInt("anx_id"));
        a.setTipo(rs.getString("anx_tipo"));
        a.setNomeOriginal(rs.getString("anx_nome_original"));
        a.setCaminho(rs.getString("anx_caminho"));
        a.setEnviadoEm(rs.getObject("anx_enviado_em", LocalDateTime.class));
        a.setAtivoId(rs.getInt("fk_atv_id"));
        return a;
    }
}
