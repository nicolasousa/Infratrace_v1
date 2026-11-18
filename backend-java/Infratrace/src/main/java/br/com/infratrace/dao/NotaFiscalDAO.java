package br.com.infratrace.dao;

import br.com.infratrace.model.NotaFiscal;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela nota_fiscal. */
@Repository
public class NotaFiscalDAO {

    private static final String COLUNAS = "SELECT nff_id, nff_num, nff_chave, nff_valor, nff_volumes, "
            + "nff_razao_social, nff_cnpj, nff_endereco, nff_inscricao, nff_descricao FROM nota_fiscal";

    public List<NotaFiscal> listar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " ORDER BY nff_id DESC");
             ResultSet rs = ps.executeQuery()) {
            List<NotaFiscal> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    public NotaFiscal buscarPorId(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE nff_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int inserir(Connection con, NotaFiscal n) throws SQLException {
        String sql = "INSERT INTO nota_fiscal (nff_num, nff_chave, nff_valor, nff_volumes, nff_razao_social, "
                + "nff_cnpj, nff_endereco, nff_inscricao, nff_descricao) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, n);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    public void atualizar(Connection con, NotaFiscal n) throws SQLException {
        String sql = "UPDATE nota_fiscal SET nff_num = ?, nff_chave = ?, nff_valor = ?, nff_volumes = ?, "
                + "nff_razao_social = ?, nff_cnpj = ?, nff_endereco = ?, nff_inscricao = ?, nff_descricao = ? "
                + "WHERE nff_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            preencher(ps, n);
            ps.setInt(10, n.getId());
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM nota_fiscal WHERE nff_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** setObject aceita null: o driver grava NULL na coluna. */
    private void preencher(PreparedStatement ps, NotaFiscal n) throws SQLException {
        ps.setInt(1, n.getNum());
        ps.setString(2, n.getChave());
        ps.setBigDecimal(3, n.getValor());
        ps.setObject(4, n.getVolumes());
        ps.setString(5, n.getRazaoSocial());
        ps.setString(6, n.getCnpj());
        ps.setString(7, n.getEndereco());
        ps.setString(8, n.getInscricao());
        ps.setString(9, n.getDescricao());
    }

    private NotaFiscal mapear(ResultSet rs) throws SQLException {
        NotaFiscal n = new NotaFiscal();
        n.setId(rs.getInt("nff_id"));
        n.setNum(rs.getInt("nff_num"));
        n.setChave(rs.getString("nff_chave"));
        n.setValor(rs.getObject("nff_valor", BigDecimal.class));
        n.setVolumes(rs.getObject("nff_volumes", Integer.class));
        n.setRazaoSocial(rs.getString("nff_razao_social"));
        n.setCnpj(rs.getString("nff_cnpj"));
        n.setEndereco(rs.getString("nff_endereco"));
        n.setInscricao(rs.getString("nff_inscricao"));
        n.setDescricao(rs.getString("nff_descricao"));
        return n;
    }
}
