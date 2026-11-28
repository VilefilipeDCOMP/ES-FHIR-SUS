package br.ufs.fhirsus.map;

import java.util.Map;
import java.util.Optional;

public record DominioValores(String nome, Map<String, String> equivalencia) {

    public DominioValores {
        equivalencia = equivalencia == null ? Map.of() : Map.copyOf(equivalencia);
    }

    /**
     * Consulta a chave cadastrada ou encontra a chave associada a um valor único.
     * A consulta por chave tem prioridade nas tabelas com códigos presentes nos dois lados.
     */
    public Optional<String> buscarEquivalente(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }

        String direto = equivalencia.get(codigo);
        if (direto != null) {
            return Optional.of(direto);
        }

        String equivalente = null;
        for (Map.Entry<String, String> par : equivalencia.entrySet()) {
            if (codigo.equals(par.getValue())) {
                if (equivalente != null) {
                    throw new IllegalStateException("O código tem mais de um equivalente no domínio.");
                }
                equivalente = par.getKey();
            }
        }
        return Optional.ofNullable(equivalente);
    }
}
