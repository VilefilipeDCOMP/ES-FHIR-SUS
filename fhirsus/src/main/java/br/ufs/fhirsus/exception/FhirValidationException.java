package br.ufs.fhirsus.exception;

/**
 * Falha de validação na entrada clínica. O gateway retorna HTTP 422 para as regras do fluxo
 * e HTTP 400 para falhas estruturais identificadas pela subclasse.
 */
public class FhirValidationException extends RuntimeException {
    private final String campo;
    private final String codigoErro;

    public FhirValidationException(String message, String campo, String codigoErro) {
        super(message);
        this.campo = campo;
        this.codigoErro = codigoErro;
    }

    public String getCampo() {
        return campo;
    }

    public String getCodigoErro() {
        return codigoErro;
    }
}
