package br.ufs.fhirsus.config.service;

import br.ufs.fhirsus.config.domain.*;
import br.ufs.fhirsus.config.entity.ConfigVersaoEntity;
import br.ufs.fhirsus.config.repository.ConfigVersaoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {

    private final ConfigParser parser = new ConfigParser();
    private final ConfigValidator validator = new ConfigValidator();
    private final ConfigVersaoRepository repository;
    private final AuditoriaService auditoria;

    public ConfigService(ConfigVersaoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    /** Só valida (não salva). Lança ConfigSintaxeException ou ConfigInvalidaException. */
    public MapeamentoConfig validar(String conteudo) {
        MapeamentoConfig config = parser.parse(conteudo);
        List<ErroValidacao> erros = validator.validar(config);
        if (!erros.isEmpty()) {
            throw new ConfigInvalidaException(erros);
        }
        return config;
    }

    /**
     * Valida, salva como nova versão ativa e audita (versão atual + anterior), tudo na mesma transação.
     * Config inválida: exceção ANTES de qualquer escrita (a ativa não muda).
     * Falha na auditoria: rollback (a ativa anterior permanece).
     */
    @Transactional
    public ConfigVersaoEntity salvar(String conteudo) {
        MapeamentoConfig config = validar(conteudo);

        Optional<ConfigVersaoEntity> anterior = repository.findByAtivaTrue();
        Integer versaoAnterior = anterior.map(ConfigVersaoEntity::getVersao).orElse(null);
        anterior.ifPresent(a -> {
            a.setAtiva(false);
            repository.saveAndFlush(a);
        });

        int versao = repository.maiorVersao() + 1;
        ConfigVersaoEntity nova = repository.save(new ConfigVersaoEntity(
                versao, config.id(), conteudo, true, true, LocalDateTime.now(), auditoria.responsavelAtual()));

        auditoria.registrar(versao, versaoAnterior);
        return nova;
    }

    @Transactional(readOnly = true)
    public Optional<ConfigVersaoEntity> ativa() {
        return repository.findByAtivaTrue();
    }

    @Transactional(readOnly = true)
    public List<ConfigVersaoEntity> versoes() {
        return repository.findAllByOrderByVersaoDesc();
    }
}
