package br.com.infratrace.dao;

import br.com.infratrace.model.Configuracao;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela configuracao (chave/valor). As chaves vêm do script do banco; aqui só lemos e alteramos o valor. */
@Repository
public class ConfiguracaoDAO {

    private static final String COLUNAS = "SELECT cfg_chave, cfg_valor, cfg_descricao FROM configuracao";

    public List<Configuracao> listar(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " ORDER BY cfg_chave");
             ResultSet rs = ps.executeQuery()) {
            List<Configuracao> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    public Configuracao buscarPorChave(Connection con, String chave) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE cfg_chave = ?")) {
            ps.setString(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public void atualizarValor(Connection con, String chave, String valor) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE configuracao SET cfg_valor = ? WHERE cfg_chave = ?")) {
            ps.setString(1, valor);
            ps.setString(2, chave);
            ps.executeUpdate();
        }
    }

    private Configuracao mapear(ResultSet rs) throws SQLException {
        Configuracao c = new Configuracao();
        c.setChave(rs.getString("cfg_chave"));
        c.setValor(rs.getString("cfg_valor"));
        c.setDescricao(rs.getString("cfg_descricao"));
        return c;
    }
}
