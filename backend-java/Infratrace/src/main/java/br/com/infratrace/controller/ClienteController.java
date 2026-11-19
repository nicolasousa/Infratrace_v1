package br.com.infratrace.controller;

import br.com.infratrace.model.Cliente;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.ClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/** Endpoints de clientes (/clientes). Filtro opcional: ?ativo=true */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<Cliente> listar(@RequestParam(required = false) Boolean ativo) throws SQLException {
        return service.listar(ativo);
    }

    @GetMapping("/{id}")
    public Cliente buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente criar(@RequestBody Cliente corpo,
                         @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Cliente atualizar(@PathVariable int id, @RequestBody Cliente corpo,
                             @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @PatchMapping("/{id}/inativar")
    public Cliente inativar(@PathVariable int id,
                            @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.inativar(id, usuario.getId());
    }
}
