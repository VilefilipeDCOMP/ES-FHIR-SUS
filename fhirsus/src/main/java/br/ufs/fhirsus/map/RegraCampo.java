package br.ufs.fhirsus.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RegraCampo {

    private final String caminhoOrigem;
    private final String caminhoDestino;
    private final TipoConversao tipoConversao;
    private final String parametroConversao;
    private final Boolean obrigatorio;
    private final String condicaoObrigatoriedade;

    public RegraCampo(String caminhoOrigem, String caminhoDestino, TipoConversao tipoConversao,
                      String parametroConversao, Boolean obrigatorio) {
        this(caminhoOrigem, caminhoDestino, tipoConversao, parametroConversao, obrigatorio, null);
    }

    @JsonCreator
    public RegraCampo(
            @JsonProperty("caminhoOrigem") String caminhoOrigem,
            @JsonProperty("caminhoDestino") String caminhoDestino,
            @JsonProperty("tipoConversao") TipoConversao tipoConversao,
            @JsonProperty("parametroConversao") String parametroConversao,
            @JsonProperty("obrigatorio") Boolean obrigatorio,
            @JsonProperty("condicaoObrigatoriedade") String condicaoObrigatoriedade) {
        this.caminhoOrigem = caminhoOrigem;
        this.caminhoDestino = caminhoDestino;
        this.tipoConversao = tipoConversao;
        this.parametroConversao = parametroConversao;
        this.obrigatorio = obrigatorio;
        this.condicaoObrigatoriedade = condicaoObrigatoriedade;
    }

    public String getCaminhoOrigem() {
        return caminhoOrigem;
    }

    public String getCaminhoDestino() {
        return caminhoDestino;
    }

    public TipoConversao getTipoConversao() {
        return tipoConversao;
    }

    public String getParametroConversao() {
        return parametroConversao;
    }

    public Boolean getObrigatorio() {
        return obrigatorio;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCondicaoObrigatoriedade() {
        return condicaoObrigatoriedade;
    }
}
