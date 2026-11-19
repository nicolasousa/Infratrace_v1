package br.com.infratrace.controller;

import br.com.infratrace.model.Usuario;
import br.com.infratrace.model.Sala;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.SalaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

/** Endpoints de salas (/salas). Sem SQL e sem regra de negócio: só HTTP -> Service. */
@RestController
@RequestMapping("/salas")
public class SalaController {

    private final SalaService service;

    public SalaController(SalaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Sala> listar() throws SQLException {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Sala buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Sala criar(@RequestBody Sala corpo,
                     @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Sala atualizar(@PathVariable int id, @RequestBody Sala corpo,
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