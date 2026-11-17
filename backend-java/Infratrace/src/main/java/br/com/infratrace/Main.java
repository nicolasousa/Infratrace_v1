package br.com.infratrace;

import br.com.infratrace.config.Conexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        String sql = "SELECT tpa_id, tpa_nome FROM tipo_ativo ORDER BY tpa_id";

        // try-with-resources: tudo que for aberto nos parênteses é fechado
        // automaticamente no fim do bloco (na ordem inversa: rs, ps, con),
        // mesmo se acontecer uma exceção.
        try (Connection con = Conexao.getConexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("Conectado! Tipos de ativo cadastrados:");

            // O ResultSet começa ANTES da primeira linha; cada next() avança uma linha
            // e devolve false quando acabam as linhas.
            while (rs.next()) {
                int id      = rs.getInt("tpa_id");
                String nome = rs.getString("tpa_nome");
                System.out.println(id + " - " + nome);
            }

        } catch (SQLException e) {
            System.err.println("Erro ao consultar o banco: " + e.getMessage());
            System.err.println("SQLState: " + e.getSQLState() + " | Código MySQL: " + e.getErrorCode());
        }
    }
}
