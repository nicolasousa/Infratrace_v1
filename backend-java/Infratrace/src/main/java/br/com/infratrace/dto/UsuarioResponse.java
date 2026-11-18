package br.com.infratrace.dto;

import br.com.infratrace.model.Usuario;

import java.time.LocalDateTime;

/**
 * Usuário como sai da API. Existe para NUNCA devolver o hash da senha nem o token de
 * recuperação, que estão no Model Usuario.
 */
public record UsuarioResponse(Integer id, String nome, String email, String perfil, Boolean ativo,
                              LocalDateTime criadoEm) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.getAtivo(), u.getCriadoEm());
    }
}