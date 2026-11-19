package br.com.infratrace.controller;

import br.com.infratrace.model.Aluguel;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.config.UsuarioResponsavelInterceptor;
import br.com.infratrace.service.AluguelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

/** Endpoints de aluguéis de rack (/alugueis). Filtros opcionais: ?clienteId=1&rackId=2 */
@RestController
@RequestMapping("/alugueis")
public class AluguelController {

    private final AluguelService service;

    public AluguelController(AluguelService service) {
        this.service = service;
    }

    @GetMapping
    public List<Aluguel> listar(@RequestParam(required = false) Integer clienteId,
                                @RequestParam(required = false) Integer rackId) throws SQLException {
        return service.listar(clienteId, rackId);
    }

    @GetMapping("/{id}")
    public Aluguel buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Aluguel criar(@RequestBody Aluguel corpo,
                         @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Aluguel atualizar(@PathVariable int id, @RequestBody Aluguel corpo,
                             @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable int id,
                        @RequestAttribute(UsuarioResponsavelInterceptor.USUARIO_RESPONSAVEL) Usuario usuario) throws SQLException {
        service.excluir(id, usuario.getId());
    }
}
