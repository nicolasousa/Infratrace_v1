package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.dao.AluguelDAO;
import br.com.infratrace.dao.AtivoDAO;
import br.com.infratrace.dto.AlertaResponse;
import br.com.infratrace.model.Aluguel;
import br.com.infratrace.model.Ativo;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Alertas de vencimento. O prazo de antecedência vem da tabela configuracao, então o ADMIN
 * pode mudar de 30 para 60 dias sem alterar código.
 */
@Service
public class AlertaService {

    private final AtivoDAO ativoDAO;
    private final AluguelDAO aluguelDAO;
    private final ConfiguracaoService configuracaoService;

    public AlertaService(AtivoDAO ativoDAO, AluguelDAO aluguelDAO, ConfiguracaoService configuracaoService) {
        this.ativoDAO = ativoDAO;
        this.aluguelDAO = aluguelDAO;
        this.configuracaoService = configuracaoService;
    }

    /** Ativos (não descartados) cuja garantia vence de hoje até hoje + N dias. */
    public AlertaResponse<Ativo> garantiasVencendo() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            int dias = configuracaoService.valorInteiro(con, ConfiguracaoService.ALERTA_GARANTIA);
            LocalDate hoje = LocalDate.now();
            LocalDate ate = hoje.plusDays(dias);
            List<Ativo> itens = ativoDAO.listarGarantiaVencendo(con, hoje, ate);
            return new AlertaResponse<>(dias, ate, itens.size(), itens);
        }
    }

    /** Aluguéis cujo contrato termina de hoje até hoje + N dias. */
    public AlertaResponse<Aluguel> alugueisVencendo() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            int dias = configuracaoService.valorInteiro(con, ConfiguracaoService.ALERTA_ALUGUEL);
            LocalDate hoje = LocalDate.now();
            LocalDate ate = hoje.plusDays(dias);
            List<Aluguel> itens = aluguelDAO.listarVencendo(con, hoje, ate);
            return new AlertaResponse<>(dias, ate, itens.size(), itens);
        }
    }
}
