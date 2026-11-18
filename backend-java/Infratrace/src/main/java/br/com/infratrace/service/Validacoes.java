package br.com.infratrace.service;

import br.com.infratrace.exception.NegocioException;

import java.util.Set;

/** Validações simples reaproveitadas pelos Services. Todas lançam NegocioException (HTTP 400). */
public final class Validacoes {

    private Validacoes() {
    }

    /** Campo de texto obrigatório: não pode ser vazio nem passar do tamanho da coluna. Devolve sem espaços nas pontas. */
    public static String texto(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new NegocioException("O campo '" + campo + "' é obrigatório");
        }
        return textoOpcional(valor, campo, tamanhoMaximo);
    }

    /** Campo de texto opcional: pode ser nulo, mas se vier precisa caber na coluna. Texto vazio vira null. */
    public static String textoOpcional(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > tamanhoMaximo) {
            throw new NegocioException("O campo '" + campo + "' aceita no máximo " + tamanhoMaximo + " caracteres");
        }
        return limpo;
    }

    public static <T> T obrigatorio(T valor, String campo) {
        if (valor == null) {
            throw new NegocioException("O campo '" + campo + "' é obrigatório");
        }
        return valor;
    }

    /** Valor precisa estar entre as opções aceitas pelo CHECK da tabela. */
    public static String opcao(String valor, String campo, Set<String> opcoes) {
        String limpo = texto(valor, campo, 64).toUpperCase();
        if (!opcoes.contains(limpo)) {
            throw new NegocioException("Valor inválido para '" + campo + "'. Use um destes: " + opcoes);
        }
        return limpo;
    }

    /** Remove pontos, barras e traços. Ex.: "11.222.333/0001-81" -> "11222333000181". */
    public static String somenteDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    /** Confere o tamanho e os dois dígitos verificadores do CNPJ. Devolve só os 14 dígitos. */
    public static String cnpj(String valor, String campo) {
        String digitos = somenteDigitos(texto(valor, campo, 20));
        if (digitos.length() != 14 || digitos.chars().distinct().count() == 1) {
            throw new NegocioException("CNPJ inválido em '" + campo + "': informe 14 dígitos");
        }
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        if (digitoCnpj(digitos, pesos1) != digitos.charAt(12) - '0'
                || digitoCnpj(digitos, pesos2) != digitos.charAt(13) - '0') {
            throw new NegocioException("CNPJ inválido em '" + campo + "': dígitos verificadores não conferem");
        }
        return digitos;
    }

    private static int digitoCnpj(String digitos, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (digitos.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
