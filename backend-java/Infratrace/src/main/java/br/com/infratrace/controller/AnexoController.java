package br.com.infratrace.controller;

import br.com.infratrace.dto.AnexoResponse;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.AnexoService;
import br.com.infratrace.service.AnexoService.ArquivoAnexo;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

/**
 * Anexos de ativos. O envio usa multipart/form-data (no Postman: Body -> form-data),
 * com os campos "tipo" (texto) e "arquivo" (File).
 */
@RestController
public class AnexoController {

    private final AnexoService service;

    public AnexoController(AnexoService service) {
        this.service = service;
    }

    @GetMapping("/ativos/{ativoId}/anexos")
    public List<AnexoResponse> listar(@PathVariable int ativoId) throws SQLException {
        return service.listarPorAtivo(ativoId);
    }

    @PostMapping(value = "/ativos/{ativoId}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AnexoResponse anexar(@PathVariable int ativoId,
                                @RequestParam("tipo") String tipo,
                                @RequestParam("arquivo") MultipartFile arquivo,
                                @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario)
            throws SQLException, IOException {
        return service.anexar(ativoId, tipo, arquivo, usuario.getId());
    }

    @GetMapping("/anexos/{id}")
    public AnexoResponse buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    /** Devolve o arquivo com o nome original (Content-Disposition: attachment). */
    @GetMapping("/anexos/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable int id) throws SQLException {
        ArquivoAnexo arquivo = service.arquivo(id);
        String nome = arquivo.anexo().getNomeOriginal();
        MediaType tipo = MediaTypeFactory.getMediaType(nome).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build().toString())
                .body(new PathResource(arquivo.caminho()));
    }

    @DeleteMapping("/anexos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable int id,
                        @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        service.excluir(id, usuario.getId());
    }
}
