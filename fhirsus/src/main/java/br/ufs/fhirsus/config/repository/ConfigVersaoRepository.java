package br.ufs.fhirsus.config.repository;

import br.ufs.fhirsus.config.entity.ConfigVersaoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConfigVersaoRepository extends JpaRepository<ConfigVersaoEntity, Long> {

    Optional<ConfigVersaoEntity> findByAtivaTrue();

    List<ConfigVersaoEntity> findAllByOrderByVersaoDesc();

    @Query("select coalesce(max(c.versao), 0) from ConfigVersaoEntity c")
    int maiorVersao();
}
