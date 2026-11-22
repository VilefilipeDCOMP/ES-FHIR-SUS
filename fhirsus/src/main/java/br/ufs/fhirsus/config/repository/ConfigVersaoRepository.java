package br.ufs.fhirsus.config.repository;

import br.ufs.fhirsus.config.entity.ConfigVersaoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConfigVersaoRepository extends JpaRepository<ConfigVersaoEntity, Long> {

    @Query(value = "select id from config_ativacao where id = 1 for update", nativeQuery = true)
    int bloquearAtivacao();

    Optional<ConfigVersaoEntity> findByAtivaTrue();

    List<ConfigVersaoEntity> findAllByOrderByVersaoDesc();

    @Query("select coalesce(max(c.versao), 0) from ConfigVersaoEntity c")
    int maiorVersao();
}
