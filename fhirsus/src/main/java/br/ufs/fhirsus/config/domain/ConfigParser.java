package br.ufs.fhirsus.config.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê a configuração em JSON (ADR-04: configuração externa em JSON; YAML descartado).
 * JSON malformado, campo desconhecido ou valor de enum inválido viram ConfigSintaxeException.
 */
public class ConfigParser {

    private static final int LIMITE_CARACTERES = 1_000_000;

    // Jackson 3 (Spring Boot 4): mapper imutável, construído via builder.
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public MapeamentoConfig parse(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new ConfigSintaxeException("Conteúdo vazio");
        }
        if (conteudo.length() > LIMITE_CARACTERES) {
            throw new ConfigSintaxeException("Conteúdo acima do limite de " + LIMITE_CARACTERES + " caracteres");
        }
        try {
            return mapper.readValue(conteudo, MapeamentoConfig.class);
        } catch (JacksonException e) {
            throw new ConfigSintaxeException("Erro de JSON: " + primeiraLinha(e.getMessage()), e);
        }
    }

    private static String primeiraLinha(String s) {
        if (s == null) {
            return "formato inválido";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
