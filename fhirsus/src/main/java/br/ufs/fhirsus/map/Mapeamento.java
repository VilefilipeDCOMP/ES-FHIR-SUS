package br.ufs.fhirsus.map;

import java.util.List;

public record Mapeamento(String recurso, List<RegraCampo> toLegado, List<RegraCampo> toFHIR) {

    public Mapeamento {
        toLegado = toLegado == null ? List.of() : List.copyOf(toLegado);
        toFHIR = toFHIR == null ? List.of() : List.copyOf(toFHIR);
    }
}
