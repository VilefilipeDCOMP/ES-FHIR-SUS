package br.ufs.fhirsus.map;

import java.util.Map;

public record DominioValores(String nome, Map<String, String> equivalencia) {

    public DominioValores {
        equivalencia = equivalencia == null ? Map.of() : Map.copyOf(equivalencia);
    }
}
