package br.com.infratrace.config;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Controle manual de transação (sem @Transactional).
 *
 * Abre uma conexão, desliga o auto-commit, executa o "trabalho" recebido e:
 *  - se tudo der certo: commit();
 *  - se qualquer passo lançar exceção: rollback() e a exceção sobe para o Controller.
 *
 * Todos os DAOs chamados dentro do trabalho usam a MESMA Connection, por isso fazem
 * parte da mesma transação. Exemplo de uso em um Service:
 *
 *     return Transacao.executar(con -> {
 *         int id = salaDAO.inserir(con, sala);
 *         auditoria.registrar(con, ...);   // se falhar aqui, o INSERT da sala é desfeito
 *         return id;
 *     });
 *
 * O MovimentacaoService mostra o mesmo processo escrito "por extenso", sem esta classe.
 */
public final class Transacao {

    private Transacao() {
    }

    @FunctionalInterface
    public interface Trabalho<T> {
        T executar(Connection con) throws SQLException;
    }

    public static <T> T executar(Trabalho<T> trabalho) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            con.setAutoCommit(false);
            try {
                T resultado = trabalho.executar(con);
                con.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }
}
