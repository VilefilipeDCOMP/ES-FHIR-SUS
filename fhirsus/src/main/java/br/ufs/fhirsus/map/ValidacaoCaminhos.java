package br.ufs.fhirsus.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.StringNode;

public final class ValidacaoCaminhos {

    private ValidacaoCaminhos() {
    }

    public static String erroOrigem(String caminho) {
        try {
            CaminhoJson.origem(caminho);
            return null;
        } catch (FalhaConversao e) {
            return e.getMessage();
        }
    }

    public static Map<Integer, String> errosDestino(List<String> destinos) {
        Map<Integer, String> erros = new LinkedHashMap<>();
        SaidaJson saida = new SaidaJson();
        for (int i = 0; i < destinos.size(); i++) {
            String destino = destinos.get(i);
            if (destino == null || destino.isBlank()) {
                continue;
            }
            try {
                saida.registrar(CaminhoJson.destino(destino), StringNode.valueOf("x"));
            } catch (FalhaConversao e) {
                erros.put(i, e.getMessage());
            }
        }
        if (erros.isEmpty()) {
            try {
                saida.construir();
            } catch (FalhaConversao e) {
                erros.put(-1, e.getMessage() + " (" + e.getCaminho() + ")");
            }
        }
        return erros;
    }
}