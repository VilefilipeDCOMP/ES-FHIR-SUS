package br.ufs.fhirsus.map;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.CAMINHO_INVALIDO;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.VALOR_INVALIDO;

record CaminhoJson(String texto, List<Segmento> segmentos) {

    private static final Pattern SEGMENTO = Pattern.compile(
            "\\.([A-Za-z_][A-Za-z0-9_-]*)|\\[(0|[1-9][0-9]*)\\]");

    static CaminhoJson origem(String caminho) {
        if (caminho == null || !caminho.startsWith("$")) {
            throw caminhoInvalido(caminho);
        }
        return analisar(caminho, caminho.substring(1));
    }

    static CaminhoJson destino(String caminho) {
        if (caminho == null) {
            throw caminhoInvalido(null);
        }
        return analisar(caminho, "." + caminho);
    }

    private static CaminhoJson analisar(String caminho, String sufixo) {
        List<Segmento> segmentos = new ArrayList<>();
        Matcher matcher = SEGMENTO.matcher(sufixo);
        int posicao = 0;
        while (matcher.find() && matcher.start() == posicao) {
            if (matcher.group(1) != null) {
                segmentos.add(new Segmento(matcher.group(1), null));
            } else {
                try {
                    segmentos.add(new Segmento(null, Integer.parseInt(matcher.group(2))));
                } catch (NumberFormatException erro) {
                    throw caminhoInvalido(caminho);
                }
            }
            posicao = matcher.end();
        }
        if (posicao != sufixo.length()) {
            throw caminhoInvalido(caminho);
        }
        return new CaminhoJson(caminho, List.copyOf(segmentos));
    }

    JsonNode ler(JsonNode raiz) {
        JsonNode atual = raiz;
        for (Segmento segmento : segmentos) {
            if (atual == null || atual.isNull()) {
                return atual;
            }
            if (segmento.ehIndice() && atual.isArray()) {
                atual = atual.get(segmento.indice());
            } else if (!segmento.ehIndice() && atual.isObject()) {
                atual = atual.get(segmento.campo());
            } else {
                throw new FalhaConversao(VALOR_INVALIDO, texto,
                        "A estrutura do JSON de origem é incompatível com o caminho.");
            }
        }
        return atual;
    }

    private static FalhaConversao caminhoInvalido(String caminho) {
        return new FalhaConversao(CAMINHO_INVALIDO, caminho, "Sintaxe de caminho não suportada.");
    }

    record Segmento(String campo, Integer indice) {

        boolean ehIndice() {
            return indice != null;
        }
    }
}
