package br.ufs.fhirsus.map;

public final class FalhaConversao extends RuntimeException {

    public enum Codigo {
        ENTRADA_INVALIDA,
        CONFIGURACAO_INVALIDA,
        MAPEAMENTO_INEXISTENTE,
        REGRA_NAO_SUPORTADA,
        CAMINHO_INVALIDO,
        CAMPO_OBRIGATORIO,
        VALOR_INVALIDO,
        DOMINIO_INEXISTENTE,
        CODIGO_SEM_EQUIVALENCIA,
        DESTINO_CONFLITANTE
    }

    private final Codigo codigo;
    private final String caminho;

    FalhaConversao(Codigo codigo, String caminho, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
        this.caminho = caminho;
    }

    public Codigo getCodigo() {
        return codigo;
    }

    public String getCaminho() {
        return caminho;
    }
}
