package br.ufs.fhirsus.config.domain;

/** JSON malformado ou com campo desconhecido/tipo errado (erro de sintaxe/esquema). */
public class ConfigSintaxeException extends RuntimeException {
    public ConfigSintaxeException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
    public ConfigSintaxeException(String mensagem) {
        super(mensagem);
    }
}
