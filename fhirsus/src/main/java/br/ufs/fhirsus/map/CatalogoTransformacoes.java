package br.ufs.fhirsus.map;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.StringNode;

public final class CatalogoTransformacoes {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:[.,][0-9]+)?");
    private static final Pattern SEM_DIGITOS = Pattern.compile("[^0-9]");
    private static final Map<String, UnaryOperator<JsonNode>> FUNCOES = Map.of(
            "apenas_numeros", valor -> StringNode.valueOf(SEM_DIGITOS.matcher(texto(valor)).replaceAll("")),
            "para_decimal", CatalogoTransformacoes::paraDecimal);

    private CatalogoTransformacoes() {}

    public static boolean aceita(String nome) {
        try {
            resolver(nome);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static UnaryOperator<JsonNode> resolver(String parametro) {
        if (parametro == null) {
            throw new IllegalArgumentException("Informe a transformação em parametroConversao.");
        }
        var funcao = FUNCOES.get(parametro);
        if (funcao != null) {
            return funcao;
        }

        var formatador = FormatacaoTemporal.compilar(parametro.strip());
        return valor -> {
            try {
                return StringNode.valueOf(formatador.apply(texto(valor)));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Valor incompatível com o formato de origem.", e);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        };
    }

    private static String texto(JsonNode valor) {
        if (!valor.isString()) {
            throw new IllegalArgumentException("A transformação exige um valor textual.");
        }
        return valor.asString();
    }

    private static JsonNode paraDecimal(JsonNode valor) {
        if (valor.isNumber()) {
            return valor;
        }
        String numero = texto(valor).strip();
        if (!DECIMAL.matcher(numero).matches()) {
            throw new IllegalArgumentException("Informe um decimal sem separador de milhares.");
        }
        return DecimalNode.valueOf(new BigDecimal(numero.replace(',', '.')));
    }
}
