package br.ufs.fhirsus.map;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.dashjoin.jsonata.Jsonata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.CAMPO_OBRIGATORIO;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.CODIGO_SEM_EQUIVALENCIA;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.CONFIGURACAO_INVALIDA;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.DOMINIO_INEXISTENTE;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.ENTRADA_INVALIDA;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.MAPEAMENTO_INEXISTENTE;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.VALOR_INVALIDO;

/** Transforma uma árvore JSON com as regras recebidas para um recurso e sentido. */
public final class MotorConversao {

    /**
     * Devolve um novo objeto JSON ou lança uma falha estruturada de conversão.
     * O payload e a configuração fornecidos continuam disponíveis ao chamador.
     */
    public ObjectNode converter(
            JsonNode payload, String recurso, SentidoConversao sentido, MapeamentoConfig configuracao) {
        if (payload == null || payload.isMissingNode() || recurso == null || recurso.isBlank() || sentido == null) {
            throw new FalhaConversao(ENTRADA_INVALIDA, null, "Informe payload, recurso e sentido.");
        }
        if (configuracao == null) {
            throw new FalhaConversao(CONFIGURACAO_INVALIDA, null, "Configuração não informada.");
        }

        List<RegraCampo> regras = selecionarRegras(recurso, sentido, configuracao);
        Map<String, DominioValores> dominios = indexarDominios(configuracao.dominios());
        SaidaJson saida = new SaidaJson();
        for (RegraCampo regra : regras) {
            CaminhoJson destino = CaminhoJson.destino(regra.getCaminhoDestino());
            if (regra.getObrigatorio() == null || regra.getTipoConversao() == null) {
                throw new FalhaConversao(CONFIGURACAO_INVALIDA, destino.texto(),
                        "A regra deve informar obrigatorio e tipoConversao.");
            }
            boolean obrigatorio = exigirValor(payload, regra);
            JsonNode valor = converterValor(payload, regra, dominios);
            if (semValor(valor)) {
                if (obrigatorio) {
                    throw new FalhaConversao(CAMPO_OBRIGATORIO, destino.texto(),
                            "O campo obrigatório ficou sem valor.");
                }
                valor = null;
            }
            saida.registrar(destino, valor);
        }
        return saida.construir();
    }

    private List<RegraCampo> selecionarRegras(
            String recurso, SentidoConversao sentido, MapeamentoConfig configuracao) {
        List<Mapeamento> mapeamentos = configuracao.mapeamentos().stream()
                .filter(mapeamento -> recurso.equals(mapeamento.recurso()))
                .toList();
        if (mapeamentos.size() > 1) {
            throw new FalhaConversao(CONFIGURACAO_INVALIDA, recurso, "Há mapeamentos duplicados para o recurso.");
        }
        if (mapeamentos.isEmpty()) {
            throw new FalhaConversao(MAPEAMENTO_INEXISTENTE, recurso, "Não há mapeamento para o recurso.");
        }
        Mapeamento mapeamento = mapeamentos.get(0);
        List<RegraCampo> regras = switch (sentido) {
            case TO_LEGADO -> mapeamento.toLegado();
            case TO_FHIR -> mapeamento.toFHIR();
        };
        if (regras.isEmpty()) {
            throw new FalhaConversao(MAPEAMENTO_INEXISTENTE, recurso,
                    "Não há regras para o sentido solicitado.");
        }
        return regras;
    }

    private Map<String, DominioValores> indexarDominios(List<DominioValores> dominios) {
        Map<String, DominioValores> porNome = new HashMap<>();
        for (DominioValores dominio : dominios) {
            String nome = dominio.nome();
            if (nome == null || nome.isBlank() || porNome.putIfAbsent(nome, dominio) != null) {
                throw new FalhaConversao(CONFIGURACAO_INVALIDA, "dominios",
                        "Cada domínio deve ter um nome preenchido e único.");
            }
        }
        return porNome;
    }

    private JsonNode converterValor(
            JsonNode payload, RegraCampo regra, Map<String, DominioValores> dominios) {
        String parametro = regra.getParametroConversao();
        return switch (regra.getTipoConversao()) {
            case DIRETA -> {
                if (parametro != null) {
                    throw parametroInvalido(regra, "DIRETA exige parametroConversao nulo.");
                }
                yield CaminhoJson.origem(regra.getCaminhoOrigem()).ler(payload);
            }
            case DOMINIO -> converterDominio(payload, regra, dominios);
            case CONSTANTE -> {
                if (parametro == null) {
                    throw parametroInvalido(regra, "CONSTANTE exige parametroConversao textual.");
                }
                yield StringNode.valueOf(parametro);
            }
            case TRANSFORMACAO -> converterTransformacao(payload, regra);
            case EXPRESSAO -> converterExpressao(payload, regra);
        };
    }

    private boolean exigirValor(JsonNode payload, RegraCampo regra) {
        if (!regra.getObrigatorio()) {
            return false;
        }
        String condicao = regra.getCondicaoObrigatoriedade();
        if (condicao == null) {
            return true;
        }
        var expressao = compilarJsonata(condicao, regra.getCaminhoDestino(), "condicaoObrigatoriedade");
        try {
            JsonNode resultado = AvaliadorJsonata.avaliar(expressao, payload, null);
            if (resultado == null || !resultado.isBoolean()) {
                throw new IllegalArgumentException("A condição de obrigatoriedade deve retornar um booleano.");
            }
            return resultado.asBoolean();
        } catch (IllegalArgumentException e) {
            throw new FalhaConversao(VALOR_INVALIDO, regra.getCaminhoDestino(),
                    "Falha na condição de obrigatoriedade. " + e.getMessage());
        }
    }

    private JsonNode converterTransformacao(JsonNode payload, RegraCampo regra) {
        UnaryOperator<JsonNode> transformacao;
        try {
            transformacao = CatalogoTransformacoes.resolver(regra.getParametroConversao());
        } catch (IllegalArgumentException e) {
            throw parametroInvalido(regra, e.getMessage());
        }
        CaminhoJson origem = CaminhoJson.origem(regra.getCaminhoOrigem());
        JsonNode valor = origem.ler(payload);
        if (semValor(valor)) {
            return valor;
        }
        try {
            return transformacao.apply(valor);
        } catch (IllegalArgumentException e) {
            throw new FalhaConversao(VALOR_INVALIDO, origem.texto(), e.getMessage());
        }
    }

    private JsonNode converterExpressao(JsonNode payload, RegraCampo regra) {
        var origem = CaminhoJson.origem(regra.getCaminhoOrigem());
        var valor = origem.ler(payload);
        var expressao = compilarJsonata(regra.getParametroConversao(), regra.getCaminhoDestino(), "parametroConversao");
        try {
            return AvaliadorJsonata.avaliar(expressao, payload, valor);
        } catch (IllegalArgumentException e) {
            throw new FalhaConversao(VALOR_INVALIDO, regra.getCaminhoDestino(), e.getMessage());
        }
    }

    private Jsonata compilarJsonata(String fonte, String destino, String campo) {
        try {
            return AvaliadorJsonata.compilar(fonte);
        } catch (IllegalArgumentException e) {
            throw new FalhaConversao(CONFIGURACAO_INVALIDA, destino, "Campo " + campo + ". " + e.getMessage());
        }
    }

    private JsonNode converterDominio(
            JsonNode payload, RegraCampo regra, Map<String, DominioValores> dominios) {
        String nome = regra.getParametroConversao();
        if (nome == null || nome.isBlank()) {
            throw parametroInvalido(regra, "DOMINIO exige o nome da tabela em parametroConversao.");
        }
        DominioValores dominio = dominios.get(nome);
        if (dominio == null) {
            throw new FalhaConversao(DOMINIO_INEXISTENTE, regra.getCaminhoDestino(),
                    "A tabela referenciada pela regra não foi configurada.");
        }
        CaminhoJson origem = CaminhoJson.origem(regra.getCaminhoOrigem());
        JsonNode valor = origem.ler(payload);
        if (semValor(valor)) {
            return valor;
        }
        if (!valor.isString()) {
            throw new FalhaConversao(VALOR_INVALIDO, origem.texto(), "DOMINIO exige um código textual.");
        }
        try {
            return dominio.buscarEquivalente(valor.asString())
                    .map(StringNode::valueOf)
                    .orElseThrow(() -> new FalhaConversao(CODIGO_SEM_EQUIVALENCIA, origem.texto(),
                            "O código de origem não possui equivalência na tabela."));
        } catch (IllegalStateException e) {
            throw new FalhaConversao(CONFIGURACAO_INVALIDA, origem.texto(), e.getMessage());
        }
    }

    private boolean semValor(JsonNode valor) {
        return valor == null || valor.isNull() || (valor.isString() && valor.asString().isBlank());
    }

    private FalhaConversao parametroInvalido(RegraCampo regra, String mensagem) {
        return new FalhaConversao(CONFIGURACAO_INVALIDA, regra.getCaminhoDestino(), mensagem);
    }
}
