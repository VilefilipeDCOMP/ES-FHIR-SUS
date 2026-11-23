package br.ufs.fhirsus.exception;

public class InvalidClinicalOutputException extends RuntimeException {
    private final String campo;

    public InvalidClinicalOutputException(String mensagem, String campo) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
