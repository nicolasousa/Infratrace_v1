package br.com.infratrace.service;

import br.com.infratrace.dao.LogAuditoriaDAO;
import br.com.infratrace.model.LogAuditoria;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Grava uma linha em log_auditoria. Sempre recebe a MESMA Connection da operação auditada:
 * assim, se a operação sofrer rollback, o log também some (não fica log de algo que não aconteceu),
 * e se o log falhar, a operação é desfeita.
 *
 * Os valores anterior e novo são gravados como JSON, usando o JsonMapper do Spring.
 */
@Component
public class Auditoria {

    public static final String INSERT = "INSERT";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String INATIVAR = "INATIVAR";

    private final LogAuditoriaDAO dao;
    private final JsonMapper json;

    public Auditoria(LogAuditoriaDAO dao, JsonMapper json) {
        this.dao = dao;
        this.json = json;
    }

    public void registrar(Connection con, int usuarioId, String acao, String entidade, int entidadeId,
                          Object valorAnterior, Object valorNovo) throws SQLException {
        LogAuditoria log = new LogAuditoria();
        log.setAcao(acao);
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);
        log.setValorAnterior(valorAnterior == null ? null : json.writeValueAsString(valorAnterior));
        log.setValorNovo(valorNovo == null ? null : json.writeValueAsString(valorNovo));
        log.setUsuarioId(usuarioId);
        dao.inserir(con, log);
    }
}
