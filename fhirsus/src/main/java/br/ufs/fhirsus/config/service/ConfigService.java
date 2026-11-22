package br.ufs.fhirsus.config.service;

import br.ufs.fhirsus.map.MapeamentoConfig;
import br.ufs.fhirsus.config.domain.*;
import br.ufs.fhirsus.config.entity.ConfigVersaoEntity;
import br.ufs.fhirsus.config.repository.ConfigVersaoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ConfigService {

    private final ConfigParser parser = new ConfigParser();
    private final ConfigValidator validator = new ConfigValidator();
    private final ConfigVersaoRepository repository;
    private final AuditoriaService auditoria;


    public MapeamentoConfig validar(String conteudo) {
        MapeamentoConfig config = parser.parse(conteudo);
        List<ErroValidacao> erros = validator.validar(config);
        if (!erros.isEmpty()) {
            throw new ConfigInvalidaException(erros);
        }
        return config;
    }

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
                versao, conteudo, true, true, LocalDateTime.now(), auditoria.responsavelAtual()));

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

    @Transactional(readOnly = true)
    public Optional<MapeamentoConfig> ativaComoModelo() {
        return repository.findByAtivaTrue().map(v -> parser.parse(v.getConteudo()));
    }
}
