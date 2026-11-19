package br.com.infratrace.dto;

/** Corpo para criar ou alterar usuário. Nesta fase não há login, então não há senha. */
public record UsuarioRequest(String nome, String email, String perfil) {
}
