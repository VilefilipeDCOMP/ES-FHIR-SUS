package br.ufs.fhirsus.map;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import tools.jackson.databind.JsonNode;

final class RelatorioConversao implements BeforeEachCallback {

    private static final boolean ATIVO = Boolean.getBoolean("map.mostrarPayloads");

    @Override
    public void beforeEach(ExtensionContext contexto) {
        if (ATIVO) {
            System.out.printf("%n[%s | %s]%n", contexto.getRequiredTestMethod().getName(),
                    contexto.getDisplayName());
        }
    }

    void conversao(JsonNode entrada, JsonNode esperado, JsonNode gerado) {
        if (ATIVO) {
            System.out.printf("Entrada%n%s%nSaída esperada%n%s%nSaída gerada%n%s%n%n",
                    entrada.toPrettyString(), esperado.toPrettyString(), gerado.toPrettyString());
        }
    }

    void falha(JsonNode entrada, FalhaConversao.Codigo codigo, String caminho, FalhaConversao gerada) {
        if (ATIVO) {
            System.out.printf("Entrada%n%s%nErro esperado%n%s%nErro recebido%n%s%n%n",
                    entrada.toPrettyString(), formatarFalha(codigo, caminho),
                    formatarFalha(gerada.getCodigo(), gerada.getCaminho()));
        }
    }

    private String formatarFalha(FalhaConversao.Codigo codigo, String caminho) {
        return caminho == null ? codigo.toString() : codigo + " em " + caminho;
    }
}
