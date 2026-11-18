package br.com.infratrace.dto;

/** Resposta do login: o token deve ir no cabeçalho "Authorization: Bearer <token>" das próximas requisições. */
public record LoginResponse(String token, UsuarioResponse usuario) {
}