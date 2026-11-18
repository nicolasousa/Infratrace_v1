package br.com.infratrace.controller;

import br.com.infratrace.dto.UsuarioRequest;
import br.com.infratrace.dto.UsuarioResponse;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.seguranca.AutenticacaoInterceptor;
import br.com.infratrace.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/** Endpoints de usuários (/usuarios). Só ADMIN acessa (regra no AutenticacaoInterceptor). */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() throws SQLException {
        return service.listar();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@RequestBody UsuarioRequest corpo,
                                 @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario)
            throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable int id, @RequestBody UsuarioRequest corpo,
                                     @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario)
            throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @PatchMapping("/{id}/inativar")
    public UsuarioResponse inativar(@PathVariable int id,
                                    @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario)
            throws SQLException {
        return service.inativar(id, usuario.getId());
    }
}
