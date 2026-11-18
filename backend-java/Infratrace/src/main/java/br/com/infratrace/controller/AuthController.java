package br.com.infratrace.controller;

import br.com.infratrace.dto.LoginRequest;
import br.com.infratrace.dto.LoginResponse;
import br.com.infratrace.dto.UsuarioRequest;
import br.com.infratrace.dto.UsuarioResponse;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.seguranca.AutenticacaoInterceptor;
import br.com.infratrace.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

/** Login, logout, usuário atual e criação do primeiro ADMIN. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioService service;

    public AuthController(UsuarioService service) {
        this.service = service;
    }

    /** Público. Funciona só enquanto não existe nenhum usuário. */
    @PostMapping("/primeiro-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse primeiroAdmin(@RequestBody UsuarioRequest corpo) throws SQLException {
        return service.criarPrimeiroAdmin(corpo);
    }

    /** Público. Devolve o token para usar em "Authorization: Bearer <token>". */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest corpo) throws SQLException {
        return service.login(corpo);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String cabecalho) {
        service.logout(cabecalho.substring("Bearer ".length()).trim());
    }

    @GetMapping("/me")
    public UsuarioResponse eu(@RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) {
        return UsuarioResponse.de(usuario);
    }
}
