package br.ufs.fhirsus.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuração do cliente HTTP para o laboratório legado.
 * Atende ao requisito de timeout configurável (RF016 / Issue API-03).
 */
@Component
@ConfigurationProperties(prefix = "lab.client")
public class LabClientProperties {

    private String baseUrl = "http://localhost:8080/mock/lab";
    private int timeoutMs = 5000;

    public LabClientProperties() {}

    public LabClientProperties(String baseUrl, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
