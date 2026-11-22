package br.ufs.fhirsus.map;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import br.ufs.fhirsus.map.CaminhoJson.Segmento;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.DESTINO_CONFLITANTE;

final class SaidaJson {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final No raiz = new No();

    void registrar(CaminhoJson caminho, JsonNode valor) {
        No atual = raiz;
        for (Segmento segmento : caminho.segmentos()) {
            if (atual.terminal || atual.temFilhosDeOutroTipo(segmento)) {
                throw conflito(caminho.texto());
            }
            atual = atual.filhos.computeIfAbsent(segmento, chave -> new No());
        }
        if (atual.terminal || !atual.filhos.isEmpty()) {
            throw conflito(caminho.texto());
        }
        // Uma regra opcional sem valor ainda reserva o destino para detectar colisões.
        atual.terminal = true;
        atual.valor = valor;
    }

    ObjectNode construir() {
        return raiz.criarObjeto("");
    }

    private static FalhaConversao conflito(String caminho) {
        return new FalhaConversao(DESTINO_CONFLITANTE, caminho,
                "O destino é duplicado ou incompatível com outra regra.");
    }

    private static final class No {

        private final Map<Segmento, No> filhos = new LinkedHashMap<>();
        private boolean terminal;
        private JsonNode valor;

        private boolean temFilhosDeOutroTipo(Segmento segmento) {
            return !filhos.isEmpty()
                    && filhos.keySet().iterator().next().ehIndice() != segmento.ehIndice();
        }

        private JsonNode criarJson(String caminho) {
            if (terminal) {
                return valor == null ? null : valor.deepCopy();
            }
            if (filhos.keySet().iterator().next().ehIndice()) {
                ArrayNode array = criarArray(caminho);
                return array.isEmpty() ? null : array;
            }
            ObjectNode objeto = criarObjeto(caminho);
            return objeto.isEmpty() ? null : objeto;
        }

        private ObjectNode criarObjeto(String caminho) {
            ObjectNode objeto = JSON.objectNode();
            for (var entrada : filhos.entrySet()) {
                String campo = entrada.getKey().campo();
                String destino = caminho.isEmpty() ? campo : caminho + "." + campo;
                JsonNode filho = entrada.getValue().criarJson(destino);
                if (filho != null) {
                    objeto.set(campo, filho);
                }
            }
            return objeto;
        }

        private ArrayNode criarArray(String caminho) {
            ArrayNode array = JSON.arrayNode();
            var entradas = filhos.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Segmento::indice)))
                    .toList();
            for (var entrada : entradas) {
                int indice = entrada.getKey().indice();
                String destino = caminho + "[" + indice + "]";
                JsonNode filho = entrada.getValue().criarJson(destino);
                if (filho != null) {
                    if (indice != array.size()) {
                        throw new FalhaConversao(DESTINO_CONFLITANTE, destino,
                                "O índice deixaria uma lacuna no array de destino.");
                    }
                    array.add(filho);
                }
            }
            return array;
        }
    }
}
