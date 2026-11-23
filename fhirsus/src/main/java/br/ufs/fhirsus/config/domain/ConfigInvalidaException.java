package br.ufs.fhirsus.config.domain;

import lombok.Getter;
import java.util.List;

public class ConfigInvalidaException extends RuntimeException {

    @Getter
    private final List<ErroValidacao> erros;

    public ConfigInvalidaException(List<ErroValidacao> erros) {
        super("Configuração inválida: " + erros.size() + " erro(s)");
        this.erros = List.copyOf(erros);
    }
}
