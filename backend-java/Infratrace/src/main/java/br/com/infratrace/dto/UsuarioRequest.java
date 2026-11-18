package br.com.infratrace.dto;

/** Corpo para criar ou alterar usuário. Na alteração, senha vazia/nula mantém a senha atual. */
public record UsuarioRequest(String nome, String email, String senha, String perfil) {
}