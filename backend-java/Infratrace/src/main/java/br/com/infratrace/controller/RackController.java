package br.com.infratrace.controller;

import br.com.infratrace.dto.OcupacaoRackResponse;
import br.com.infratrace.model.Rack;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.RackService;
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

/** Endpoints de racks (/racks), incluindo o mapa de ocupação. */
@RestController
@RequestMapping("/racks")
public class RackController {

    private final RackService service;

    public RackController(RackService service) {
        this.service = service;
    }

    @GetMapping
    public List<Rack> listar(@RequestParam(required = false) Integer fileiraId) throws SQLException {
        return service.listar(fileiraId);
    }

    @GetMapping("/{id}")
    public Rack buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    /** Slots ocupados (e por quem), slots livres e percentual de ocupação. */
    @GetMapping("/{id}/ocupacao")
    public OcupacaoRackResponse ocupacao(@PathVariable int id) throws SQLException {
        return service.ocupacao(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Rack criar(@RequestBody Rack corpo,
                      @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Rack atualizar(@PathVariable int id, @RequestBody Rack corpo,
                          @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable int id,
                        @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        service.excluir(id, usuario.getId());
    }
}
