package br.com.infratrace.dao;

import br.com.infratrace.model.LogAuditoria;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela log_auditoria. O log só recebe INSERT: nunca é alterado nem apagado. */
@Repository
public class LogAuditoriaDAO {

    private static final String COLUNAS = "SELECT log_id, log_acao, log_entidade, log_entidade_id, log_data_hora, "
            + "log_valor_anterior, log_valor_novo, fk_usr_id FROM log_auditoria";

    /** Filtros opcionais; devolve no máximo "limite" registros, do mais recente para o mais antigo. */
    public List<LogAuditoria> listar(Connection con, String entidade, Integer entidadeId, Integer usuarioId, int limite)
            throws SQLException {
        StringBuilder sql = new StringBuilder(COLUNAS).append(" WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();
        if (entidade != null) {
            sql.append(" AND log_entidade = ?");
            parametros.add(entidade);
        }
        if (entidadeId != null) {
            sql.append(" AND log_entidade_id = ?");
            parametros.add(entidadeId);
        }
        if (usuarioId != null) {
            sql.append(" AND fk_usr_id = ?");
            parametros.add(usuarioId);
        }
        sql.append(" ORDER BY log_data_hora DESC, log_id DESC LIMIT ?");
        parametros.add(limite);
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<LogAuditoria> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public void inserir(Connection con, LogAuditoria log) throws SQLException {
        String sql = "INSERT INTO log_auditoria (log_acao, log_entidade, log_entidade_id, log_valor_anterior, "
                + "log_valor_novo, fk_usr_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, log.getAcao());
            ps.setString(2, log.getEntidade());
            ps.setInt(3, log.getEntidadeId());
            ps.setString(4, log.getValorAnterior());
            ps.setString(5, log.getValorNovo());
            ps.setInt(6, log.getUsuarioId());
            ps.executeUpdate();
        }
    }

    private LogAuditoria mapear(ResultSet rs) throws SQLException {
        LogAuditoria l = new LogAuditoria();
        l.setId(rs.getInt("log_id"));
        l.setAcao(rs.getString("log_acao"));
        l.setEntidade(rs.getString("log_entidade"));
        l.setEntidadeId(rs.getInt("log_entidade_id"));
        l.setDataHora(rs.getObject("log_data_hora", LocalDateTime.class));
        l.setValorAnterior(rs.getString("log_valor_anterior"));
        l.setValorNovo(rs.getString("log_valor_novo"));
        l.setUsuarioId(rs.getInt("fk_usr_id"));
        return l;
    }
}
