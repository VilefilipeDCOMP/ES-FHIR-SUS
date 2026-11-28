package br.ufs.fhirsus.map;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.StringNode;

public final class CatalogoTransformacoes {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATA_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:[.,][0-9]+)?");
    private static final Pattern SEM_DIGITOS = Pattern.compile("[^0-9]");
    private static final Map<String, UnaryOperator<JsonNode>> FUNCOES = Map.of(
            "apenas_numeros", valor -> StringNode.valueOf(SEM_DIGITOS.matcher(texto(valor)).replaceAll("")),
            "para_decimal", CatalogoTransformacoes::paraDecimal,
            "data_para_iso", valor -> converterData(valor, DATA_BR, DATA_ISO),
            "iso_para_data", valor -> converterData(valor, DATA_ISO, DATA_BR));

    private CatalogoTransformacoes() {}

    public static boolean aceita(String nome) {
        return nome != null && FUNCOES.containsKey(nome);
    }

    static JsonNode aplicar(String nome, JsonNode valor) {
        return FUNCOES.get(nome).apply(valor);
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

    private static JsonNode converterData(JsonNode valor, DateTimeFormatter origem, DateTimeFormatter destino) {
        String data = texto(valor);
        try {
            LocalDate dia = LocalDate.parse(data, origem);
            return StringNode.valueOf(dia.format(destino));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida para o formato da transformação.");
        }
    }
}
