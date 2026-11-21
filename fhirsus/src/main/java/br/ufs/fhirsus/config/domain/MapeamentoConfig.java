package br.ufs.fhirsus.config.domain;

import java.util.List;

/**
 * Raiz da configuração (diagrama: MapeamentoConfig). O atributo {@code validado} do diagrama
 * não vem no arquivo: é resultado da validação (ver ConfigVersaoEntity.validada).
 */
public record MapeamentoConfig(String id, List<Mapeamento> mapeamentos, List<DominioValores> dominiosValores) {
}
