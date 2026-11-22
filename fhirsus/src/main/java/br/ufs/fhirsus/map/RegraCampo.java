package br.ufs.fhirsus.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RegraCampo {

    private final String caminhoOrigem;
    private final String caminhoDestino;
    private final TipoConversao tipoConversao;
    private final String parametroConversao;
    private final Boolean obrigatorio;

    @JsonCreator
    public RegraCampo(
            @JsonProperty("caminhoOrigem") String caminhoOrigem,
            @JsonProperty("caminhoDestino") String caminhoDestino,
            @JsonProperty("tipoConversao") TipoConversao tipoConversao,
            @JsonProperty("parametroConversao") String parametroConversao,
            @JsonProperty("obrigatorio") Boolean obrigatorio) {
        this.caminhoOrigem = caminhoOrigem;
        this.caminhoDestino = caminhoDestino;
        this.tipoConversao = tipoConversao;
        this.parametroConversao = parametroConversao;
        this.obrigatorio = obrigatorio;
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
}
