package br.ufs.fhirsus.config.domain;

import java.util.List;

public record MapeamentoConfig(String id, List<Mapeamento> mapeamentos, List<DominioValores> dominiosValores) {
}
