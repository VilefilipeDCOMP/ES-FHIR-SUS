package br.ufs.fhirsus.engine;

import br.ufs.fhirsus.map.SentidoConversao;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contrato de integração CT-02 entre o Gateway e o Motor de Conversão (Luiz Manoel - CONV-01..05).
 */
public interface ConversionEngine {

    ObjectNode converter(JsonNode payload, String recurso, SentidoConversao sentido);
}
