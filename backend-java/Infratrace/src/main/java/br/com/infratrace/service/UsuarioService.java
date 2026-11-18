package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.UsuarioDAO;
import br.com.infratrace.dto.LoginRequest;
import br.com.infratrace.dto.LoginResponse;
import br.com.infratrace.dto.UsuarioRequest;
import br.com.infratrace.dto.UsuarioResponse;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoAutorizadoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.seguranca.SenhaHash;
import br.com.infratrace.seguranca.Sessoes;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static br.com.infratrace.service.Validacoes.opcao;
import static br.com.infratrace.service.Validacoes.texto;

/**
 * Usuários, login e perfis (RF-05).
 *  - A senha só existe em memória durante a requisição; no banco vai o hash (SenhaHash).
 *  - E-mail é único e guardado em minúsculas.
 *  - Usuários não são excluídos (são referenciados no log e nas movimentações): são inativados.
 *  - O sistema nunca pode ficar sem nenhum ADMIN ativo.
 */
@Service
public class UsuarioService {

    private static final String ENTIDADE = "usuario";
    public static final Set<String> PERFIS = Set.of("ADMIN", "TECNICO", "AUDITOR");
    private static final int SENHA_MINIMA = 8;

    private final UsuarioDAO dao;
    private final Sessoes sessoes;
    private final Auditoria auditoria;

    public UsuarioService(UsuarioDAO dao, Sessoes sessoes, Auditoria auditoria) {
        this.dao = dao;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
    }

    // ---------------------------------------------------------------- login

    public LoginResponse login(LoginRequest pedido) throws SQLException {
        if (pedido.email() == null || pedido.senha() == null) {
            throw new NaoAutorizadoException("Informe e-mail e senha");
        }
        Usuario usuario;
        try (Connection con = Conexao.getConexao()) {
            usuario = dao.buscarPorEmail(con, pedido.email().trim().toLowerCase());
        }
        // Mesma mensagem para "e-mail não existe" e "senha errada": não revela quais e-mails existem.
        if (usuario == null || !SenhaHash.confere(pedido.senha(), usuario.getSenhaHash())) {
            throw new NaoAutorizadoException("E-mail ou senha inválidos");
        }
        if (!usuario.getAtivo()) {
            throw new NaoAutorizadoException("Usuário inativo");
        }
        return new LoginResponse(sessoes.abrir(usuario.getId()), UsuarioResponse.de(usuario));
    }

    public void logout(String token) {
        sessoes.encerrar(token);
    }

    /**
     * Cria o primeiro ADMIN. Só funciona com a tabela usuario vazia; depois disso, novos usuários
     * são criados por um ADMIN logado em POST /usuarios. Evita deixar senha de admin no script SQL.
     */
    public UsuarioResponse criarPrimeiroAdmin(UsuarioRequest pedido) throws SQLException {
        Usuario novo = montarNovo(pedido, "ADMIN");
        return Transacao.executar(con -> {
            if (dao.contar(con) > 0) {
                throw new ConflitoException("Já existem usuários cadastrados. Peça a um ADMIN para criar seu acesso.");
            }
            novo.setId(dao.inserir(con, novo));
            UsuarioResponse resposta = UsuarioResponse.de(dao.buscarPorId(con, novo.getId()));
            auditoria.registrar(con, novo.getId(), Auditoria.INSERT, ENTIDADE, novo.getId(), null, resposta);
            return resposta;
        });
    }

    // ---------------------------------------------------------------- CRUD

    public List<UsuarioResponse> listar() throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return dao.listar(con).stream().map(UsuarioResponse::de).toList();
        }
    }

    public UsuarioResponse buscar(int id) throws SQLException {
        try (Connection con = Conexao.getConexao()) {
            return UsuarioResponse.de(obter(con, id));
        }
    }

    public UsuarioResponse criar(UsuarioRequest pedido, int usuarioLogadoId) throws SQLException {
        Usuario novo = montarNovo(pedido, null);
        return Transacao.executar(con -> {
            conferirEmailLivre(con, novo.getEmail(), null);
            novo.setId(dao.inserir(con, novo));
            UsuarioResponse resposta = UsuarioResponse.de(dao.buscarPorId(con, novo.getId()));
            auditoria.registrar(con, usuarioLogadoId, Auditoria.INSERT, ENTIDADE, novo.getId(), null, resposta);
            return resposta;
        });
    }

    /** Altera nome, e-mail e perfil. Se "senha" vier preenchida, troca a senha também. */
    public UsuarioResponse atualizar(int id, UsuarioRequest pedido, int usuarioLogadoId) throws SQLException {
        String nome = texto(pedido.nome(), "nome", 128);
        String email = email(pedido.email());
        String perfil = opcao(pedido.perfil(), "perfil", PERFIS);
        String novaSenha = pedido.senha() == null || pedido.senha().isEmpty() ? null : senha(pedido.senha());

        return Transacao.executar(con -> {
            Usuario atual = obter(con, id);
            UsuarioResponse anterior = UsuarioResponse.de(atual);
            conferirEmailLivre(con, email, id);
            if ("ADMIN".equals(atual.getPerfil()) && !"ADMIN".equals(perfil)) {
                conferirOutroAdmin(con, id);
            }
            atual.setNome(nome);
            atual.setEmail(email);
            atual.setPerfil(perfil);
            if (novaSenha != null) {
                atual.setSenhaHash(SenhaHash.gerar(novaSenha));
            }
            dao.atualizar(con, atual);
            UsuarioResponse resposta = UsuarioResponse.de(atual);
            auditoria.registrar(con, usuarioLogadoId, Auditoria.UPDATE, ENTIDADE, id, anterior, resposta);
            return resposta;
        });
    }

    public UsuarioResponse inativar(int id, int usuarioLogadoId) throws SQLException {
        if (id == usuarioLogadoId) {
            throw new NegocioException("Você não pode inativar o próprio usuário");
        }
        UsuarioResponse resposta = Transacao.executar(con -> {
            Usuario atual = obter(con, id);
            if (!atual.getAtivo()) {
                throw new ConflitoException("O usuário já está inativo");
            }
            if ("ADMIN".equals(atual.getPerfil())) {
                conferirOutroAdmin(con, id);
            }
            UsuarioResponse anterior = UsuarioResponse.de(atual);
            atual.setAtivo(false);
            dao.atualizar(con, atual);
            UsuarioResponse depois = UsuarioResponse.de(atual);
            auditoria.registrar(con, usuarioLogadoId, Auditoria.INATIVAR, ENTIDADE, id, anterior, depois);
            return depois;
        });
        sessoes.encerrarTodasDoUsuario(id);   // derruba quem estiver logado com esse usuário
        return resposta;
    }

    // ---------------------------------------------------------------- auxiliares

    private Usuario obter(Connection con, int id) throws SQLException {
        Usuario u = dao.buscarPorId(con, id);
        if (u == null) {
            throw new NaoEncontradoException("Usuário " + id + " não encontrado");
        }
        return u;
    }

    private Usuario montarNovo(UsuarioRequest pedido, String perfilForcado) {
        Usuario u = new Usuario();
        u.setNome(texto(pedido.nome(), "nome", 128));
        u.setEmail(email(pedido.email()));
        u.setPerfil(perfilForcado != null ? perfilForcado : opcao(pedido.perfil(), "perfil", PERFIS));
        u.setSenhaHash(SenhaHash.gerar(senha(pedido.senha())));
        u.setAtivo(true);
        return u;
    }

    private void conferirEmailLivre(Connection con, String email, Integer idAtual) throws SQLException {
        Usuario existente = dao.buscarPorEmail(con, email);
        if (existente != null && !existente.getId().equals(idAtual)) {
            throw new ConflitoException("Já existe um usuário com o e-mail " + email);
        }
    }

    private void conferirOutroAdmin(Connection con, int id) throws SQLException {
        if (dao.contarAdminsAtivos(con, id) == 0) {
            throw new ConflitoException("O sistema precisa de pelo menos um ADMIN ativo");
        }
    }

    private static String email(String valor) {
        String email = texto(valor, "email", 128).toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new NegocioException("E-mail inválido");
        }
        return email;
    }

    private static String senha(String valor) {
        if (valor == null || valor.length() < SENHA_MINIMA) {
            throw new NegocioException("A senha deve ter pelo menos " + SENHA_MINIMA + " caracteres");
        }
        return valor;
    }
}
