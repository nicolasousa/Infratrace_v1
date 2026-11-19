package br.com.infratrace.config;

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

/**
 * Descobre QUEM está fazendo cada alteração (POST, PUT, PATCH, DELETE).
 *
 * Nesta fase o sistema não tem login. Mesmo assim, o banco exige um usuário responsável em
 * log_auditoria, movimentacao e manutencao (fk_usr_id NOT NULL). Por isso:
 *  - se a requisição tiver o cabeçalho "X-Usuario-Id: 2", a alteração é registrada em nome do
 *    usuário 2 (que precisa existir e estar ativo);
 *  - sem o cabeçalho, ela é registrada em nome do usuário padrão: o primeiro ADMIN ativo
 *    (o script do banco já cria o "Administrador").
 *
 * O usuário encontrado fica guardado na requisição; os Controllers o recebem com
 * @RequestAttribute(USUARIO_RESPONSAVEL). Consultas (GET) passam direto.
 */
@Component
public class UsuarioResponsavelInterceptor implements HandlerInterceptor {

    public static final String USUARIO_RESPONSAVEL = "usuarioResponsavel";
    public static final String CABECALHO = "X-Usuario-Id";

    private final UsuarioDAO usuarioDAO;
    private final JsonMapper json;

    public UsuarioResponsavelInterceptor(UsuarioDAO usuarioDAO, JsonMapper json) {
        this.usuarioDAO = usuarioDAO;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException, SQLException {
        String metodo = request.getMethod();
        if ("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo)) {
            return true;
        }

        String cabecalho = request.getHeader(CABECALHO);
        Usuario usuario;
        try (Connection con = Conexao.getConexao()) {
            if (cabecalho == null || cabecalho.isBlank()) {
                usuario = usuarioDAO.buscarAdminPadrao(con);
                if (usuario == null) {
                    return recusar(response, HttpStatus.INTERNAL_SERVER_ERROR,
                            "Não há nenhum ADMIN ativo para registrar a alteração. Rode o script do banco "
                                    + "ou envie o cabeçalho " + CABECALHO + ".");
                }
            } else {
                int id;
                try {
                    id = Integer.parseInt(cabecalho.trim());
                } catch (NumberFormatException e) {
                    return recusar(response, HttpStatus.BAD_REQUEST, "O cabeçalho " + CABECALHO + " deve ser um número");
                }
                usuario = usuarioDAO.buscarPorId(con, id);
                if (usuario == null || !usuario.getAtivo()) {
                    return recusar(response, HttpStatus.BAD_REQUEST,
                            "Usuário " + id + " (cabeçalho " + CABECALHO + ") não existe ou está inativo");
                }
            }
        }
        request.setAttribute(USUARIO_RESPONSAVEL, usuario);
        return true;
    }

    private boolean recusar(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(RespostaErro.de(status.value(), status.getReasonPhrase(), mensagem)));
        return false;
    }
}
