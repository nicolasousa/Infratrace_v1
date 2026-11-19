package br.com.infratrace.controller;

import br.com.infratrace.dto.AlertaResponse;
import br.com.infratrace.dto.ConfiguracaoRequest;
import br.com.infratrace.model.Aluguel;
import br.com.infratrace.model.Ativo;
import br.com.infratrace.model.Configuracao;
import br.com.infratrace.model.LogAuditoria;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.AlertaService;
import br.com.infratrace.service.ConfiguracaoService;
import br.com.infratrace.service.ConsultaAuditoriaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

/** Configurações (/configuracoes), alertas (/alertas) e log de auditoria (/auditoria). */
@RestController
public class ConsultasController {

    private final ConfiguracaoService configuracaoService;
    private final AlertaService alertaService;
    private final ConsultaAuditoriaService auditoriaService;

    public ConsultasController(ConfiguracaoService configuracaoService, AlertaService alertaService,
                               ConsultaAuditoriaService auditoriaService) {
        this.configuracaoService = configuracaoService;
        this.alertaService = alertaService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/configuracoes")
    public List<Configuracao> configuracoes() throws SQLException {
        return configuracaoService.listar();
    }

    @GetMapping("/configuracoes/{chave}")
    public Configuracao configuracao(@PathVariable String chave) throws SQLException {
        return configuracaoService.buscar(chave);
    }

    @PutMapping("/configuracoes/{chave}")
    public Configuracao alterarConfiguracao(@PathVariable String chave, @RequestBody ConfiguracaoRequest corpo,
                                            @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario)
            throws SQLException {
        return configuracaoService.atualizar(chave, corpo.valor(), usuario.getId());
    }

    @GetMapping("/alertas/garantias")
    public AlertaResponse<Ativo> garantias() throws SQLException {
        return alertaService.garantiasVencendo();
    }

    @GetMapping("/alertas/alugueis")
    public AlertaResponse<Aluguel> alugueis() throws SQLException {
        return alertaService.alugueisVencendo();
    }

    /** Filtros opcionais: ?entidade=ativo&entidadeId=1&usuarioId=1&limite=50 */
    @GetMapping("/auditoria")
    public List<LogAuditoria> auditoria(@RequestParam(required = false) String entidade,
                                        @RequestParam(required = false) Integer entidadeId,
                                        @RequestParam(required = false) Integer usuarioId,
                                        @RequestParam(required = false) Integer limite) throws SQLException {
        return auditoriaService.listar(entidade, entidadeId, usuarioId, limite);
    }
}
