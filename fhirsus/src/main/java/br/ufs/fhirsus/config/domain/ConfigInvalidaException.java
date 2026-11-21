package br.ufs.fhirsus.config.domain;

import java.util.List;

/** Configuração sintaticamente válida, mas com erros semânticos. Nunca é salva. */
public class ConfigInvalidaException extends RuntimeException {
    private final List<ErroValidacao> erros;

    public ConfigInvalidaException(List<ErroValidacao> erros) {
        super("Configuração inválida: " + erros.size() + " erro(s)");
        this.erros = List.copyOf(erros);
    }

    public List<ErroValidacao> getErros() {
        return erros;
    }
}
