package br.ufs.fhirsus.config.web;

import br.ufs.fhirsus.config.entity.AuditoriaMapeamentoEntity;
import br.ufs.fhirsus.config.entity.ConfigVersaoEntity;
import br.ufs.fhirsus.config.repository.AuditoriaMapeamentoRepository;
import br.ufs.fhirsus.config.service.ConfigService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ConfigController {

    private final ConfigService service;
    private final AuditoriaMapeamentoRepository auditoriaRepository;

    public ConfigController(ConfigService service, AuditoriaMapeamentoRepository auditoriaRepository) {
        this.service = service;
        this.auditoriaRepository = auditoriaRepository;
    }

    @PostMapping(value = "/config", consumes = "*/*")
    public ResponseEntity<Map<String, Object>> salvar(@RequestBody String corpo) {
        ConfigVersaoEntity v = service.salvar(corpo);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumo(v));
    }

    @PostMapping(value = "/config/validar", consumes = "*/*")
    public Map<String, Object> validar(@RequestBody String corpo) {
        service.validar(corpo);
        return Map.of("valida", true);
    }

    @GetMapping("/config/ativa")
    public ResponseEntity<?> ativa() {
        return service.ativa()
                .<ResponseEntity<?>>map(v -> ResponseEntity.ok(Map.of(
                        "versao", v.getVersao(), "conteudo", v.getConteudo())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("erro", "SEM_CONFIG_ATIVA", "mensagem", "Nenhuma configuração ativa")));
    }

    @GetMapping("/config/versoes")
    public List<Map<String, Object>> versoes() {
        return service.versoes().stream().map(ConfigController::resumo).toList();
    }

    @GetMapping("/auditoria")
    public List<Map<String, Object>> auditoria() {
        return auditoriaRepository.findAllByOrderByIdDesc().stream().map(ConfigController::resumo).toList();
    }

    private static Map<String, Object> resumo(ConfigVersaoEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versao", v.getVersao());
        m.put("ativa", v.isAtiva());
        m.put("validada", v.isValidada());
        m.put("criadoEm", v.getCriadoEm().toString());
        m.put("criadoPor", v.getCriadoPor());
        return m;
    }

    private static Map<String, Object> resumo(AuditoriaMapeamentoEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("datahora", a.getDatahora().toString());
        m.put("versaoAtual", a.getVersaoAtual());
        m.put("versaoAnterior", a.getVersaoAnterior());
        m.put("responsavel", a.getResponsavel());
        return m;
    }
}
