package DAO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica de conexões com o MySQL.
 *
 * Os dados de acesso NÃO ficam no código: são lidos das variáveis de ambiente
 * INFRATRACE_DB_URL, INFRATRACE_DB_USER e INFRATRACE_DB_PASSWORD.
 *
 * Quem chama este método é responsável por fechar a conexão, de preferência com
 * try-with-resources:
 *
 *     try (Connection con = Conexao.getConexao()) { ... }
 */
public class Conexao {

    public static Connection getConexao() throws SQLException {
        String url     = lerVariavel("INFRATRACE_DB_URL");
        String usuario = lerVariavel("INFRATRACE_DB_USER");
        String senha   = lerVariavel("INFRATRACE_DB_PASSWORD");

        // Se algo der errado (senha errada, banco desligado...), o DriverManager
        // lança SQLException e ela sobe para quem chamou. Não devolvemos null.
        return DriverManager.getConnection(url, usuario, senha);
    }

    private static String lerVariavel(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + nome + " não definida. "
                    + "Configure em Run > Edit Configurations > Environment variables.");
        }
        return valor;
    }
}
