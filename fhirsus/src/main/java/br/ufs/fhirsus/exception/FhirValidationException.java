package br.ufs.fhirsus.exception;

/**
 * Exceção lançada quando a requisição for sintaticamente JSON válido, mas falhar na validação semântica FHIR
 * ou faltar campos obrigatórios do recorte (HTTP 422).
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
