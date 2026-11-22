package br.ufs.fhirsus.config.domain;

public class ConfigSintaxeException extends RuntimeException {
    public ConfigSintaxeException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
    public ConfigSintaxeException(String mensagem) {
        super(mensagem);
    }
}
