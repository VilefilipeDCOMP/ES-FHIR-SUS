package br.ufs.fhirsus.engine;

/**
 * Exceção lançada pelo motor de conversão em caso de falha nas regras De-Para.
 * Mapeada pelo Gateway para HTTP 422 com OperationOutcome (CT-02, CT-04).
 */
public class FalhaConversao extends RuntimeException {
    private final String codigo;
    private final String caminho;

    public FalhaConversao(String codigo, String caminho, String message) {
        super(message);
        this.codigo = codigo;
        this.caminho = caminho;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getCaminho() {
        return caminho;
    }
}
