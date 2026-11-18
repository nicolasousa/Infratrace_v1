package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.ConfiguracaoDAO;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Configuracao;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.infratrace.service.Validacoes.texto;

/**
 * Configurações do sistema (prazos de alerta, RF-03.4 e RF-07.5).
 * As chaves existem no script do banco; a API só altera o valor, dentro das opções permitidas.
 */
@Service
public class ConfiguracaoService {

    public static final String ALERTA_GARANTIA = "alerta_garantia_dias";
    public static final String ALERTA_ALUGUEL = "alerta_aluguel_dias";

    /** Valores aceitos para cada chave (conforme a descrição gravada no banco). */
    private static final Map<String, Set<String>> OPCOES = Map.of(
            ALERTA_GARANTIA, Set.of("30", "60", "90"),
            ALERTA_ALUGUEL, Set.of("15", "30", "60"));

    private final ConfiguracaoDAO dao;
    private final Auditoria auditoria;

    public ConfiguracaoService(ConfiguracaoDAO dao, Auditoria auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

    public List<Configuracao> listar() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con);
        }
    }

    public Configuracao buscar(String chave) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return obter(con, chave);
        }
    }

    /** Lê uma configuração numérica (usado pelos alertas). */
    public int valorInteiro(Connection con, String chave) throws SQLException {
        return Integer.parseInt(obter(con, chave).getValor());
    }

    /**
     * log_entidade_id é INT e a chave da configuração é texto: por isso o log usa 0 como id
     * e a chave aparece dentro dos valores anterior/novo.
     */
    public Configuracao atualizar(String chave, String valor, int usuarioId) throws SQLException {
        String novoValor = texto(valor, "valor", 256);
        Set<String> permitidos = OPCOES.get(chave);
        if (permitidos != null && !permitidos.contains(novoValor)) {
            throw new NegocioException("Valor inválido para " + chave + ". Use um destes: " + permitidos);
        }
        return Transacao.executar(con -> {
            Configuracao anterior = obter(con, chave);
            dao.atualizarValor(con, chave, novoValor);
            Configuracao atualizada = obter(con, chave);
            auditoria.registrar(con, usuarioId, Auditoria.UPDATE, "configuracao", 0, anterior, atualizada);
            return atualizada;
        });
    }

    private Configuracao obter(Connection con, String chave) throws SQLException {
        Configuracao c = dao.buscarPorChave(con, chave);
        if (c == null) {
            throw new NaoEncontradoException("Configuração '" + chave + "' não encontrada");
        }
        return c;
    }
}
