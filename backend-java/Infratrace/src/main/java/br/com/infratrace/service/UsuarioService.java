package br.com.infratrace.service;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.config.Transacao;
import br.com.infratrace.dao.UsuarioDAO;
import br.com.infratrace.dto.UsuarioRequest;
import br.com.infratrace.dto.UsuarioResponse;
import br.com.infratrace.exception.ConflitoException;
import br.com.infratrace.exception.NaoEncontradoException;
import br.com.infratrace.exception.NegocioException;
import br.com.infratrace.model.Usuario;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static br.com.infratrace.service.Validacoes.opcao;
import static br.com.infratrace.service.Validacoes.texto;

/**
 * Cadastro de usuários (quem opera o sistema).
 *
 * Nesta fase NÃO há login: o usuário serve para registrar quem fez cada movimentação, manutenção
 * e alteração (log_auditoria). O perfil (ADMIN, TECNICO, AUDITOR) é guardado, mas ainda não
 * restringe acesso.
 *  - E-mail é único e guardado em minúsculas.
 *  - Usuários não são excluídos (são referenciados no log e nas movimentações): são inativados.
 *  - O sistema nunca fica sem nenhum ADMIN ativo, porque é ele que responde pelas alterações
 *    enviadas sem o cabeçalho X-Usuario-Id.
 */
@Service
public class UsuarioService {

    private static final String ENTIDADE = "usuario";
    public static final Set<String> PERFIS = Set.of("ADMIN", "TECNICO", "AUDITOR");

    /** usr_senha_hash é NOT NULL no banco. Sem login, gravamos este marcador no lugar do hash. */
    private static final String SEM_LOGIN = "SEM_LOGIN";

    private final UsuarioDAO dao;
    private final Auditoria auditoria;

    public UsuarioService(UsuarioDAO dao, Auditoria auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

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

    public UsuarioResponse criar(UsuarioRequest pedido, int responsavelId) throws SQLException {
        Usuario novo = new Usuario();
        novo.setNome(texto(pedido.nome(), "nome", 128));
        novo.setEmail(email(pedido.email()));
        novo.setPerfil(opcao(pedido.perfil(), "perfil", PERFIS));
        novo.setSenhaHash(SEM_LOGIN);
        novo.setAtivo(true);
        return Transacao.executar(con -> {
            conferirEmailLivre(con, novo.getEmail(), null);
            novo.setId(dao.inserir(con, novo));
            UsuarioResponse resposta = UsuarioResponse.de(dao.buscarPorId(con, novo.getId()));
            auditoria.registrar(con, responsavelId, Auditoria.INSERT, ENTIDADE, novo.getId(), null, resposta);
            return resposta;
        });
    }

    public UsuarioResponse atualizar(int id, UsuarioRequest pedido, int responsavelId) throws SQLException {
        String nome = texto(pedido.nome(), "nome", 128);
        String email = email(pedido.email());
        String perfil = opcao(pedido.perfil(), "perfil", PERFIS);

        return Transacao.executar(con -> {
            Usuario atual = obter(con, id);
            UsuarioResponse anterior = UsuarioResponse.de(atual);
            conferirEmailLivre(con, email, id);
            if ("ADMIN".equals(atual.getPerfil()) && !"ADMIN".equals(perfil) && atual.getAtivo()) {
                conferirOutroAdmin(con, id);
            }
            atual.setNome(nome);
            atual.setEmail(email);
            atual.setPerfil(perfil);
            dao.atualizar(con, atual);
            UsuarioResponse resposta = UsuarioResponse.de(atual);
            auditoria.registrar(con, responsavelId, Auditoria.UPDATE, ENTIDADE, id, anterior, resposta);
            return resposta;
        });
    }

    public UsuarioResponse inativar(int id, int responsavelId) throws SQLException {
        if (id == responsavelId) {
            throw new NegocioException("O usuário responsável pela requisição não pode inativar a si mesmo");
        }
        return Transacao.executar(con -> {
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
            auditoria.registrar(con, responsavelId, Auditoria.INATIVAR, ENTIDADE, id, anterior, depois);
            return depois;
        });
    }

    private Usuario obter(Connection con, int id) throws SQLException {
        Usuario u = dao.buscarPorId(con, id);
        if (u == null) {
            throw new NaoEncontradoException("Usuário " + id + " não encontrado");
        }
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
}
