package br.ufs.fhirsus.map;

import java.util.List;

public record MapeamentoConfig(List<Mapeamento> mapeamentos, List<DominioValores> dominios) {

    public MapeamentoConfig {
        mapeamentos = mapeamentos == null ? List.of() : List.copyOf(mapeamentos);
        dominios = dominios == null ? List.of() : List.copyOf(dominios);
    }
}
