package br.ufs.fhirsus.map;

import java.util.List;
import java.util.Map;

import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class AvaliadorJsonata {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int LIMITE_EXPRESSAO = 2_048;
    private static final long TEMPO_MS = 250;
    private static final int PROFUNDIDADE = 64;
    private static final int LIMITE_RESULTADO = 32_768;

    private AvaliadorJsonata() {}

    public static void validar(String expressao) {
        compilar(expressao);
    }

    static Jsonata compilar(String expressao) {
        if (expressao == null || expressao.isBlank() || expressao.length() > LIMITE_EXPRESSAO) {
            throw new IllegalArgumentException("Informe uma expressão JSONata de até " + LIMITE_EXPRESSAO + " caracteres.");
        }
        try {
            return Jsonata.jsonata(expressao);
        } catch (JException e) {
            throw new IllegalArgumentException("Sintaxe JSONata inválida (" + e.getError() + ").");
        } catch (RuntimeException | StackOverflowError e) {
            throw new IllegalArgumentException("Não foi possível compilar a expressão JSONata.");
        }
    }

    static JsonNode avaliar(Jsonata expressao, JsonNode raiz, JsonNode valor) {
        Object entrada = JSON.convertValue(raiz, Object.class);
        var contexto = expressao.createFrame();
        contexto.bind("raiz", raiz.isNull() ? Jsonata.NULL_VALUE : entrada);
        if (valor != null) {
            contexto.bind("valor", valor.isNull() ? Jsonata.NULL_VALUE : JSON.convertValue(valor, Object.class));
        }
        // A expressão vem da configuração validada. Bloqueia a compilação de fórmulas a partir do payload.
        contexto.bind("eval", (Object) null);
        // Os limites do Frame são cooperativos entre etapas do avaliador.
        contexto.setRuntimeBounds(TEMPO_MS, PROFUNDIDADE);

        Object resultado;
        try {
            resultado = expressao.evaluate(entrada, contexto);
        } catch (JException e) {
            throw new IllegalArgumentException("Falha na avaliação JSONata (" + e.getError() + ").");
        } catch (RuntimeException | StackOverflowError e) {
            throw new IllegalArgumentException("Não foi possível avaliar a expressão JSONata.");
        }
        medir(resultado, LIMITE_RESULTADO, 0);
        return resultado == null ? null : JSON.valueToTree(resultado);
    }

    private static int medir(Object valor, int saldo, int profundidade) {
        if (saldo <= 0 || profundidade > PROFUNDIDADE) {
            throw new IllegalArgumentException("Resultado JSONata acima dos limites de tamanho ou profundidade.");
        }
        if (valor instanceof Map<?, ?> objeto) {
            for (var campo : objeto.entrySet()) {
                saldo = medir(campo.getKey(), saldo, profundidade + 1);
                saldo = medir(campo.getValue(), saldo, profundidade + 1);
            }
        } else if (valor instanceof List<?> lista) {
            for (Object item : lista) {
                saldo = medir(item, saldo, profundidade + 1);
            }
        } else if (valor instanceof String texto) {
            saldo -= texto.length();
        } else if (valor instanceof Number numero) {
            if (!Double.isFinite(numero.doubleValue())) {
                throw new IllegalArgumentException("A expressão produziu um número não finito.");
            }
            saldo -= numero.toString().length();
        } else if (valor != null && !(valor instanceof Boolean)) {
            throw new IllegalArgumentException("A expressão deve produzir um valor JSON.");
        }
        if (saldo <= 0) {
            throw new IllegalArgumentException("Resultado JSONata acima do limite de tamanho.");
        }
        return saldo - 1;
    }
}
