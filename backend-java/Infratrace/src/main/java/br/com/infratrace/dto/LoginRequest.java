package br.com.infratrace.dto;

/** Corpo de POST /auth/login. */
public record LoginRequest(String email, String senha) {
}