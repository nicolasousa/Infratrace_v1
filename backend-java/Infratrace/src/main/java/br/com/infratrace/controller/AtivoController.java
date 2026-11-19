package br.com.infratrace.controller;

import br.com.infratrace.dto.DescarteRequest;
import br.com.infratrace.model.Ativo;
import br.com.infratrace.model.Manutencao;
import br.com.infratrace.model.Movimentacao;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.AtivoService;
import br.com.infratrace.service.ManutencaoService;
import br.com.infratrace.service.MovimentacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

/** Endpoints de ativos (/ativos). Filtros opcionais: ?status=ATIVO&rackId=1 */
@RestController
@RequestMapping("/ativos")
public class AtivoController {

    private final AtivoService service;
    private final MovimentacaoService movimentacaoService;
    private final ManutencaoService manutencaoService;

    public AtivoController(AtivoService service, MovimentacaoService movimentacaoService,
                           ManutencaoService manutencaoService) {
        this.service = service;
        this.movimentacaoService = movimentacaoService;
        this.manutencaoService = manutencaoService;
    }

    @GetMapping
    public List<Ativo> listar(@RequestParam(required = false) String status,
                              @RequestParam(required = false) Integer rackId) throws SQLException {
        return service.listar(status, rackId);
    }

    @GetMapping("/{id}")
    public Ativo buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    /** Histórico de movimentações do ativo, da mais recente para a mais antiga. */
    @GetMapping("/{id}/movimentacoes")
    public List<Movimentacao> movimentacoes(@PathVariable int id) throws SQLException {
        service.buscar(id);                    // 404 se o ativo não existir
        return movimentacaoService.listar(id);
    }

    @GetMapping("/{id}/manutencoes")
    public List<Manutencao> manutencoes(@PathVariable int id) throws SQLException {
        service.buscar(id);
        return manutencaoService.listar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ativo criar(@RequestBody Ativo corpo,
                       @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Ativo atualizar(@PathVariable int id, @RequestBody Ativo corpo,
                           @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @PostMapping("/{id}/descarte")
    public Ativo descartar(@PathVariable int id, @RequestBody DescarteRequest corpo,
                           @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.descartar(id, corpo, usuario.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable int id,
                        @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        service.excluir(id, usuario.getId());
    }
}
