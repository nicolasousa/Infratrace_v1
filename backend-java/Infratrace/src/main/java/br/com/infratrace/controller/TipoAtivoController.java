package br.com.infratrace.controller;

import br.com.infratrace.model.Usuario;
import br.com.infratrace.model.TipoAtivo;
import br.com.infratrace.seguranca.AutenticacaoInterceptor;
import br.com.infratrace.service.TipoAtivoService;
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

/** Endpoints de tipos de ativo (/tipos-ativo). Sem SQL e sem regra de negócio: só HTTP -> Service. */
@RestController
@RequestMapping("/tipos-ativo")
public class TipoAtivoController {

    private final TipoAtivoService service;

    public TipoAtivoController(TipoAtivoService service) {
        this.service = service;
    }

    @GetMapping
    public List<TipoAtivo> listar() throws SQLException {
        return service.listar();
    }

    @GetMapping("/{id}")
    public TipoAtivo buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipoAtivo criar(@RequestBody TipoAtivo corpo,
                     @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public TipoAtivo atualizar(@PathVariable int id, @RequestBody TipoAtivo corpo,
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