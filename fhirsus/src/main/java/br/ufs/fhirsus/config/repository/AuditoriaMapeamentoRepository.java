package br.ufs.fhirsus.config.repository;

import br.ufs.fhirsus.config.entity.AuditoriaMapeamentoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaMapeamentoRepository extends JpaRepository<AuditoriaMapeamentoEntity, Long> {

    List<AuditoriaMapeamentoEntity> findAllByOrderByIdDesc();
}
