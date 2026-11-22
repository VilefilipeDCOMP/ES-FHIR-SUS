package br.ufs.fhirsus.config.service;

import br.ufs.fhirsus.config.entity.AuditoriaMapeamentoEntity;
import br.ufs.fhirsus.config.repository.AuditoriaMapeamentoRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {

    private final AuditoriaMapeamentoRepository repository;
    private final String responsavel;

    public AuditoriaService(AuditoriaMapeamentoRepository repository,
                            @Value("${fhirsus.auditoria.usuario-fixo:sistema-demo}") String responsavel) {
        this.repository = repository;
        this.responsavel = responsavel;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(int versaoAtual, Integer versaoAnterior) {
        repository.save(new AuditoriaMapeamentoEntity(LocalDateTime.now(), versaoAtual, versaoAnterior, responsavel));
    }

    public String responsavelAtual() {
        return responsavel;
    }
}
