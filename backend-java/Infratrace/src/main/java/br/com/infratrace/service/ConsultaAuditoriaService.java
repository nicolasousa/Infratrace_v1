package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.dao.LogAuditoriaDAO;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.LogAuditoria;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Consulta do log de auditoria (GET /auditoria). A gravação fica na classe Auditoria. */
@Service
public class ConsultaAuditoriaService {

    private static final int LIMITE_MAXIMO = 1000;

    private final LogAuditoriaDAO dao;

    public ConsultaAuditoriaService(LogAuditoriaDAO dao) {
        this.dao = dao;
    }

    public List<LogAuditoria> listar(String entidade, Integer entidadeId, Integer usuarioId, Integer limite)
            throws SQLException {
        int quantidade = limite == null ? 200 : limite;
        if (quantidade < 1 || quantidade > LIMITE_MAXIMO) {
            throw new NegocioException("O limite deve estar entre 1 e " + LIMITE_MAXIMO);
        }
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con, entidade, entidadeId, usuarioId, quantidade);
        }
    }
}
