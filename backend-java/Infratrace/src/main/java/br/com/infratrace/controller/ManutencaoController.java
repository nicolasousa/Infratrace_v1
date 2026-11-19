package br.com.infratrace.controller;

import br.com.infratrace.dto.FinalizarManutencaoRequest;
import br.com.infratrace.model.Manutencao;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.ManutencaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

/** Endpoints de manutenções (/manutencoes). */
@RestController
@RequestMapping("/manutencoes")
public class ManutencaoController {

    private final ManutencaoService service;

    public ManutencaoController(ManutencaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Manutencao> listar(@RequestParam(required = false) Integer ativoId) throws SQLException {
        return service.listar(ativoId);
    }

    @GetMapping("/{id}")
    public Manutencao buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Manutencao criar(@RequestBody Manutencao corpo,
                            @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Manutencao atualizar(@PathVariable int id, @RequestBody Manutencao corpo,
                                @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario)
            throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    /** O corpo é opcional: sem ele, a manutenção é finalizada com a data de hoje. */
    @PatchMapping("/{id}/finalizar")
    public Manutencao finalizar(@PathVariable int id, @RequestBody(required = false) FinalizarManutencaoRequest corpo,
                                @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario)
            throws SQLException {
        return service.finalizar(id, corpo, usuario.getId());
    }
}
