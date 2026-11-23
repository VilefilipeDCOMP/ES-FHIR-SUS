package br.ufs.fhirsus.mock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock do Sistema Legado de Laboratório para o recorte P3.
 * Permite simulação de respostas normais, falhas remotas e atrasos para validação de timeout.
 */
@RestController
@RequestMapping("/mock/lab")
public class LabMockController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/pedidos", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receberPedidoLegado(
            @RequestBody JsonNode pedidoLegado,
            @RequestHeader(value = "X-Mock-Fail", required = false, defaultValue = "false") boolean simularFalha,
            @RequestHeader(value = "X-Mock-Delay-Ms", required = false, defaultValue = "0") long delayMs
    ) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (simularFalha) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"erro\": \"Sistema laboratorial indisponível no momento\"}");
        }

        if (pedidoLegado == null || pedidoLegado.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"erro\": \"Payload legado vazio ou malformado\"}");
        }

        String idPedido = pedidoLegado.path("codigo_pedido").asText(null);
        if (idPedido == null) {
            idPedido = pedidoLegado.path("numeroPedido").asText(null);
        }

        String idExame = pedidoLegado.path("codigo_exame").asText(null);
        if (idExame == null) {
            idExame = pedidoLegado.path("codigoExame").asText(null);
        }

        if (idPedido == null || idPedido.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"erro\": \"Campo obrigatorio 'codigo_pedido' ausente no pedido legado\"}");
        }

        if (idExame == null || idExame.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"erro\": \"Campo obrigatorio 'codigo_exame' ausente no pedido legado\"}");
        }

        ObjectNode resposta = objectMapper.createObjectNode();
        resposta.put("status_execucao", "CONCLUIDO");
        resposta.put("numero_protocolo_lab", "LAB-" + System.currentTimeMillis());
        resposta.put("codigo_pedido", idPedido);
        resposta.put("numeroPedido", idPedido);

        var resultados = resposta.putArray("laudo_itens");
        ObjectNode item = resultados.addObject();
        item.put("codigo_analise", idExame);
        item.put("codigoExame", idExame);
        item.put("resultado_valor", "99 mg/dL");
        item.put("resultado", 99.0);
        item.put("status_analise", "FINAL");
        item.put("situacao", "LIBERADO");
        item.put("observacao_tecnica", "Resultado dentro da faixa de referência sintética do P3");

        return ResponseEntity.ok(resposta);
    }
}
