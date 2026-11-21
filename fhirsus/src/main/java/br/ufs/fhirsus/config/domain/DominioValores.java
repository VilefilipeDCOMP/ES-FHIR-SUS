package br.ufs.fhirsus.config.domain;

import java.util.Map;

/** Tabela de equivalência de valores (diagrama: DominioValores). */
public record DominioValores(String nome, Map<String, String> equivalencia) {
}
