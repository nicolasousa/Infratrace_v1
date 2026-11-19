package br.com.infratrace.controller;

import br.com.infratrace.model.Usuario;
import br.com.infratrace.model.NotaFiscal;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.NotaFiscalService;
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

/** Endpoints de notas fiscais (/notas-fiscais). Sem SQL e sem regra de negócio: só HTTP -> Service. */
@RestController
@RequestMapping("/notas-fiscais")
public class NotaFiscalController {

    private final NotaFiscalService service;

    public NotaFiscalController(NotaFiscalService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotaFiscal> listar() throws SQLException {
        return service.listar();
    }

    @GetMapping("/{id}")
    public NotaFiscal buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotaFiscal criar(@RequestBody NotaFiscal corpo,
                     @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public NotaFiscal atualizar(@PathVariable int id, @RequestBody NotaFiscal corpo,
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