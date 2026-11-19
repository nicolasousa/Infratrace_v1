package br.com.infratrace.exception;

import br.com.infratrace.dto.RespostaErro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.sql.SQLException;

/**
 * Converte exceções em respostas HTTP com JSON (RespostaErro).
 *
 * Com isso os Controllers não precisam de try/catch: a exceção "sobe" do DAO ou do Service
 * e o Spring chama o método correspondente aqui.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<RespostaErro> naoEncontrado(NaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<RespostaErro> negocio(NegocioException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<RespostaErro> conflito(ConflitoException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespostaErro> jsonInvalido(HttpMessageNotReadableException e) {
        return resposta(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido. Confira o JSON, os tipos dos campos e o formato das datas (AAAA-MM-DD).");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RespostaErro> arquivoGrande(MaxUploadSizeExceededException e) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo grande demais. O limite é de 10 MB por anexo.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<RespostaErro> parteFaltando(MissingServletRequestPartException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Campo '" + e.getRequestPartName() + "' não enviado no form-data");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RespostaErro> parametroFaltando(MissingServletRequestParameterException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro '" + e.getParameterName() + "' não enviado");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<RespostaErro> multipartInvalido(MultipartException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Envie o arquivo como multipart/form-data (campos 'tipo' e 'arquivo')");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespostaErro> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + e.getName() + "'");
    }

    /**
     * Erros vindos do MySQL. O getErrorCode() é o código numérico do MySQL:
     *  1644 = SIGNAL SQLSTATE '45000' disparado por um dos nossos triggers;
     *  1062 = valor repetido em coluna UNIQUE;
     *  1451 = tentativa de apagar um registro que outro registro referencia (FK);
     *  1452 = FK apontando para um registro que não existe;
     *  3819 = violação de CHECK constraint.
     */
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<RespostaErro> erroDeBanco(SQLException e) {
        return switch (e.getErrorCode()) {
            case 1644 -> resposta(HttpStatus.CONFLICT, e.getMessage());
            case 1062 -> resposta(HttpStatus.CONFLICT, "Já existe um registro com esse valor: " + e.getMessage());
            case 1451 -> resposta(HttpStatus.CONFLICT,
                    "Não é possível excluir: existem outros registros ligados a este.");
            case 1452 -> resposta(HttpStatus.BAD_REQUEST, "Referência inválida: o registro relacionado não existe.");
            case 3819 -> resposta(HttpStatus.BAD_REQUEST, "Valor não permitido pelo banco: " + e.getMessage());
            default -> {
                log.error("Erro de banco não tratado (código {})", e.getErrorCode(), e);
                yield resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro no banco de dados.");
            }
        };
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RespostaErro> configuracao(IllegalStateException e) {
        log.error("Erro de configuração", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /** Qualquer outro erro. Erros do próprio Spring (rota inexistente, método errado...) mantêm o status deles. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErro> generico(Exception e) {
        if (e instanceof ErrorResponse erroSpring) {
            HttpStatus status = HttpStatus.valueOf(erroSpring.getStatusCode().value());
            return resposta(status, status.getReasonPhrase());
        }
        log.error("Erro inesperado", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor.");
    }

    private ResponseEntity<RespostaErro> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status)
                .body(RespostaErro.de(status.value(), status.getReasonPhrase(), mensagem));
    }
}
