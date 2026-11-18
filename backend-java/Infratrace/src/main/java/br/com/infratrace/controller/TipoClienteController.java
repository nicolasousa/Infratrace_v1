package br.com.infratrace.controller;

import br.com.infratrace.model.Usuario;
import br.com.infratrace.model.TipoCliente;
import br.com.infratrace.seguranca.AutenticacaoInterceptor;
import br.com.infratrace.service.TipoClienteService;
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

/** Endpoints de tipos de cliente (/tipos-cliente). Sem SQL e sem regra de negócio: só HTTP -> Service. */
@RestController
@RequestMapping("/tipos-cliente")
public class TipoClienteController {

    private final TipoClienteService service;

    public TipoClienteController(TipoClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<TipoCliente> listar() throws SQLException {
        return service.listar();
    }

    @GetMapping("/{id}")
    public TipoCliente buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipoCliente criar(@RequestBody TipoCliente corpo,
                     @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public TipoCliente atualizar(@PathVariable int id, @RequestBody TipoCliente corpo,
                         @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable int id,
                        @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        service.excluir(id, usuario.getId());
    }
}