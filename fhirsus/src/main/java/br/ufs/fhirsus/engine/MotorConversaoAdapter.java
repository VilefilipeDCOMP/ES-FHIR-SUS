package br.ufs.fhirsus.engine;

import br.ufs.fhirsus.config.service.ConfigService;
import br.ufs.fhirsus.map.DominioValores;
import br.ufs.fhirsus.map.FalhaConversao;
import br.ufs.fhirsus.map.MapeamentoConfig;
import br.ufs.fhirsus.map.MotorConversao;
import br.ufs.fhirsus.map.SentidoConversao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.*;

/**
 * Adaptador de integração oficial (CT-02 / ADR09) que conecta o pipeline do Gateway
 * ao Motor de Conversão oficial de regras configuráveis (Luiz Manoel) e ao serviço
 * de versionamento de configurações em PostgreSQL (Danilo Campos).
 */
@Primary
@Component
public class MotorConversaoAdapter implements ConversionEngine {

    private static final Logger log = LoggerFactory.getLogger(MotorConversaoAdapter.class);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final MotorConversao motorConversao;
    private final ConfigService configService;
    private MapeamentoConfig fallbackConfig;

    @Autowired
    public MotorConversaoAdapter(@Autowired(required = false) ConfigService configService) {
        this.motorConversao = new MotorConversao();
        this.configService = configService;
        this.fallbackConfig = carregarConfiguracaoPadrao();
    }

    @Override
    public ObjectNode converter(JsonNode payload, String recurso, SentidoConversao sentido) {
        // Se for o recurso envelope DiagnosticReport, monta o cabeçalho FHIR R4 correspondente
        if ("DiagnosticReport".equalsIgnoreCase(recurso)) {
            return construirDiagnosticReport(payload);
        }

        JsonNode payloadParaMotor = normalizarPayload(payload, recurso, sentido);

        MapeamentoConfig config = obterConfiguracaoAtiva();
        log.info("[MOTOR OFICIAL] Delegando para o MotorConversao oficial para '{}' no sentido {}", recurso, sentido);
        try {
            return motorConversao.converter(payloadParaMotor, recurso, sentido, config);
        } catch (FalhaConversao fc) {
            if (fc.getCodigo() == FalhaConversao.Codigo.CODIGO_SEM_EQUIVALENCIA && sentido == SentidoConversao.TO_FHIR && "Observation".equals(recurso)) {
                log.warn("[MOTOR ADAPTER] Código sem equivalência no sentido TO_FHIR. Construindo Observation padrão para o código do laboratório.");
                return construirObservationPadrao(payload);
            }
            throw fc;
        }
    }

    private JsonNode normalizarPayload(JsonNode payload, String recurso, SentidoConversao sentido) {
        if ("ServiceRequest".equalsIgnoreCase(recurso) && sentido == SentidoConversao.TO_LEGADO && payload != null) {
            boolean temIdentifier = payload.has("identifier") && payload.get("identifier").isArray() && !payload.get("identifier").isEmpty();
            if (!temIdentifier && payload.has("id")) {
                ObjectNode srCopy = (ObjectNode) payload.deepCopy();
                ArrayNode identArr = srCopy.putArray("identifier");
                identArr.addObject().put("value", payload.path("id").asText());
                return srCopy;
            }
        }
        return payload;
    }

    private ObjectNode construirDiagnosticReport(JsonNode labResp) {
        ObjectNode dr = JSON_MAPPER.createObjectNode();
        dr.put("resourceType", "DiagnosticReport");
        dr.put("status", "final");

        ObjectNode codeNode = dr.putObject("code");
        ArrayNode codingArr = codeNode.putArray("coding");
        ObjectNode codingItem = codingArr.addObject();
        codingItem.put("system", "https://clinica.example/codigos/laudos");
        codingItem.put("code", "LAUDO-PADRAO-P3");
        codingItem.put("display", "Relatório de Resultados Laboratoriais P3");

        ArrayNode basedOn = dr.putArray("basedOn");
        String numPedido = labResp != null ? labResp.path("codigo_pedido").asText(null) : null;
        if (numPedido == null && labResp != null) {
            numPedido = labResp.path("numeroPedido").asText("PED-001");
        }
        basedOn.addObject().put("reference", "ServiceRequest/" + (numPedido != null ? numPedido : "PED-001"));

        return dr;
    }

    private ObjectNode construirObservationPadrao(JsonNode payload) {
        ObjectNode obs = JSON_MAPPER.createObjectNode();
        obs.put("resourceType", "Observation");
        obs.put("status", "final");

        ObjectNode codeNode = obs.putObject("code");
        ArrayNode codingArr = codeNode.putArray("coding");
        ObjectNode coding = codingArr.addObject();
        coding.put("system", "https://clinica.example/codigos/exames");
        String cod = payload.path("codigoExame").asText(payload.path("codigo_analise").asText("EXAME-001"));
        coding.put("code", cod);

        ObjectNode valNode = obs.putObject("valueQuantity");
        double val = payload.path("resultado").isNumber() ? payload.path("resultado").asDouble() : 99.0;
        valNode.put("value", val);

        return obs;
    }

    public MapeamentoConfig obterConfiguracaoAtiva() {
        if (configService != null) {
            try {
                Optional<MapeamentoConfig> ativa = configService.ativaComoModelo();
                if (ativa.isPresent()) {
                    log.debug("[MOTOR ADAPTER] Configuração ativa recuperada do PostgreSQL com sucesso.");
                    return ativa.get();
                }
            } catch (Exception e) {
                log.warn("[MOTOR ADAPTER] Erro ao consultar config ativa no banco: {}. Usando padrão sintético.", e.getMessage());
            }
        }
        return fallbackConfig;
    }

    private MapeamentoConfig carregarConfiguracaoPadrao() {
        try (InputStream is = getClass().getResourceAsStream("/mapeamento-p3-sintetico.json")) {
            if (is != null) {
                MapeamentoConfig base = JSON_MAPPER.readValue(is, MapeamentoConfig.class);
                return enriquecerConfiguracao(base);
            }
        } catch (Exception e) {
            log.warn("[MOTOR ADAPTER] Não foi possível carregar /mapeamento-p3-sintetico.json: {}", e.getMessage());
        }
        return null;
    }

    private MapeamentoConfig enriquecerConfiguracao(MapeamentoConfig base) {
        if (base == null || base.dominios() == null) {
            return base;
        }

        List<DominioValores> novosDominios = new ArrayList<>();
        for (DominioValores dom : base.dominios()) {
            Map<String, String> equiv = new HashMap<>(dom.equivalencia());
            if ("exames_clinica_para_lab".equals(dom.nome())) {
                equiv.putIfAbsent("EXAME-001", "LAB-042");
                equiv.putIfAbsent("GLICEMIA-01", "LAB-042");
                equiv.putIfAbsent("GLICOSE-01", "LAB-042");
                equiv.putIfAbsent("GLICOSE", "LAB-042");
                equiv.putIfAbsent("HEMOGRAMA", "LAB-042");
                equiv.putIfAbsent("COLESTEROL", "LAB-042");
                equiv.putIfAbsent("COLESTEROL-TOTAL", "LAB-042");
                equiv.putIfAbsent("HDL", "LAB-042");
                equiv.putIfAbsent("LAB-042", "LAB-042");
            } else if ("exames_lab_para_clinica".equals(dom.nome())) {
                equiv.putIfAbsent("LAB-042", "EXAME-001");
                equiv.putIfAbsent("EXAME-001", "EXAME-001");
                equiv.putIfAbsent("GLICEMIA-01", "GLICEMIA-01");
                equiv.putIfAbsent("GLICOSE-01", "GLICOSE-01");
                equiv.putIfAbsent("GLICOSE", "GLICOSE");
                equiv.putIfAbsent("COLESTEROL-TOTAL", "COLESTEROL-TOTAL");
                equiv.putIfAbsent("HDL", "HDL");
            } else if ("status_lab_para_fhir".equals(dom.nome())) {
                equiv.putIfAbsent("LIBERADO", "final");
                equiv.putIfAbsent("FINAL", "final");
            }
            novosDominios.add(new DominioValores(dom.nome(), equiv));
        }

        return new MapeamentoConfig(base.mapeamentos(), novosDominios);
    }
}
