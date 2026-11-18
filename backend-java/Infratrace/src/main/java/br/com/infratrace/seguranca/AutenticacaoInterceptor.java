package br.com.infratrace.seguranca;

import br.com.infratrace.config.Conexao;
import br.com.infratrace.dao.UsuarioDAO;
import br.com.infratrace.dto.RespostaErro;
import br.com.infratrace.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * Roda ANTES de cada Controller (registrado em config.WebConfig) e decide se a requisição passa.
 *
 * 1. Rotas públicas (login e criação do primeiro admin) passam direto.
 * 2. Lê o cabeçalho "Authorization: Bearer <token>" e descobre o usuário da sessão -> senão, 401.
 * 3. Confere se o perfil do usuário pode usar aquele método + caminho -> senão, 403.
 * 4. Guarda o usuário na requisição; os Controllers o recebem com @RequestAttribute.
 *
 * Permissões:
 *  - ADMIN: tudo.
 *  - TECNICO: consulta tudo (menos usuários e auditoria) e altera ativos, movimentações,
 *             manutenções e notas fiscais.
 *  - AUDITOR: só consulta (GET), incluindo a auditoria; não acessa usuários.
 */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    public static final String USUARIO_LOGADO = "usuarioLogado";

    private static final Set<String> ROTAS_PUBLICAS = Set.of("/auth/login", "/auth/primeiro-admin");
    private static final Set<String> ESCRITA_TECNICO = Set.of("/ativos", "/movimentacoes", "/manutencoes", "/notas-fiscais");

    private final Sessoes sessoes;
    private final UsuarioDAO usuarioDAO;
    private final JsonMapper json;

    public AutenticacaoInterceptor(Sessoes sessoes, UsuarioDAO usuarioDAO, JsonMapper json) {
        this.sessoes = sessoes;
        this.usuarioDAO = usuarioDAO;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException, SQLException {
        String caminho = request.getRequestURI();
        if (ROTAS_PUBLICAS.contains(caminho) || "OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String cabecalho = request.getHeader("Authorization");
        Integer usuarioId = null;
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            usuarioId = sessoes.usuarioDoToken(cabecalho.substring(7).trim());
        }
        if (usuarioId == null) {
            return negar(response, HttpStatus.UNAUTHORIZED, "Faça login e envie o token no cabeçalho Authorization: Bearer <token>");
        }

        Usuario usuario;
        try (Connection con = Conexao.getConexao()) {
            usuario = usuarioDAO.buscarPorId(con, usuarioId);
        }
        if (usuario == null || !usuario.getAtivo()) {
            return negar(response, HttpStatus.UNAUTHORIZED, "Usuário inexistente ou inativo");
        }

        if (!permite(usuario.getPerfil(), request.getMethod(), caminho)) {
            return negar(response, HttpStatus.FORBIDDEN,
                    "O perfil " + usuario.getPerfil() + " não tem permissão para " + request.getMethod() + " " + caminho);
        }

        request.setAttribute(USUARIO_LOGADO, usuario);
        return true;
    }

    static boolean permite(String perfil, String metodo, String caminho) {
        boolean leitura = "GET".equals(metodo);
        if (caminho.startsWith("/auth/")) {
            return true;                       // logout e "quem sou eu" valem para qualquer logado
        }
        if ("ADMIN".equals(perfil)) {
            return true;
        }
        if (caminho.startsWith("/usuarios")) {
            return false;
        }
        if ("AUDITOR".equals(perfil)) {
            return leitura;
        }
        if ("TECNICO".equals(perfil)) {
            if (caminho.startsWith("/auditoria")) {
                return false;
            }
            return leitura || ESCRITA_TECNICO.stream().anyMatch(caminho::startsWith);
        }
        return false;
    }

    private boolean negar(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(RespostaErro.de(status.value(), status.getReasonPhrase(), mensagem)));
        return false;
    }
}
