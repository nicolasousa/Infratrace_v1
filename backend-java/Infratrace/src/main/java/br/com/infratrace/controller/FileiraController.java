package br.com.infratrace.controller;

import br.com.infratrace.model.Fileira;
import br.com.infratrace.model.Usuario;
import br.com.infratrace.seguranca.AutenticacaoInterceptor;
import br.com.infratrace.service.FileiraService;
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

/** Endpoints de fileiras (/fileiras). Filtro opcional: ?salaId=1 */
@RestController
@RequestMapping("/fileiras")
public class FileiraController {

    private final FileiraService service;

    public FileiraController(FileiraService service) {
        this.service = service;
    }

    @GetMapping
    public List<Fileira> listar(@RequestParam(required = false) Integer salaId) throws SQLException {
        return service.listar(salaId);
    }

    @GetMapping("/{id}")
    public Fileira buscar(@PathVariable int id) throws SQLException {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Fileira criar(@RequestBody Fileira corpo,
                         @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        return service.criar(corpo, usuario.getId());
    }

    @PutMapping("/{id}")
    public Fileira atualizar(@PathVariable int id, @RequestBody Fileira corpo,
                             @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        return service.atualizar(id, corpo, usuario.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable int id,
                        @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO) Usuario usuario) throws SQLException {
        service.excluir(id, usuario.getId());
    }
}
