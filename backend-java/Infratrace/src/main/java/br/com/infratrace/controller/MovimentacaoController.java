package br.com.infratrace.controller;

import br.com.infratrace.dto.MovimentacaoRequest;
import br.com.infratrace.model.Movimentacao;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.MovimentacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

/** Endpoints de movimentações (/movimentacoes). Não há PUT nem DELETE: é histórico. */
@RestController
@RequestMapping("/movimentacoes")
public class MovimentacaoController {

    private final MovimentacaoService service;

    public MovimentacaoController(MovimentacaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Movimentacao> listar(@RequestParam(required = false) Integer ativoId) throws SQLException {
        return service.listar(ativoId);
    }

    @GetMapping("/{id}")
    public Movimentacao buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Movimentacao movimentar(@RequestBody MovimentacaoRequest corpo,
                                   @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario)
            throws SQLException {
        return service.movimentar(corpo, usuario.getId());
    }
}
