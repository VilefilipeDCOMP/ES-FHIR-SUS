# Relatório de Evidências de Testes — API / Gateway (P3)
**Projeto:** FHIR SUS+ · **Recorte:** Projeto Integrador P3  
**Frente:** API / Gateway · **Responsável:** Bruno Carvalho Santana Rocha (@BrunocsRocha)  
**Data da Execução:** 22/11/2026 · **Ambiente:** Java 21 / Spring Boot 4.0.10 / Maven 3.9  

---

## 1. Resumo Executivo da Suíte de Testes

A suíte automatizada de testes do Gateway foi construída para cobrir a matriz completa de cenários de sucesso, validação sintática e semântica, resiliência de rede e conformidade com os contratos HL7 FHIR R4 acordados (CT-01, CT-02, CT-04, CT-05, ADR03, ADR09 e UC-01 do P2).

* **Total de Testes Executados:** 29 testes
* **Total de Falhas / Erros:** 0 falhas / 0 erros
* **Taxa de Sucesso:** 100% (`BUILD SUCCESS`)
* **Tempo Total de Execução:** 25.060 segundos

---

## 2. Matriz Completa de Cenários Integrados (`GatewayScenariosTestSuiteTest`)

Esta suíte consome diretamente as fixtures sintéticas padronizadas em `src/test/resources/fixtures/`:

| Cenário | Descrição do Teste | Fixture de Entrada | Resposta Esperada | Status HTTP | Resultado |
|:---:|:---|:---|:---|:---:|:---:|
| **01** | Sucesso com Bundle completo (`ServiceRequest` + `Patient`) | `solicitacao-bundle-entrada.json` | `Bundle` collection com `DiagnosticReport` e `Observation` | **`200 OK`** | ✅ PASSOU |
| **02** | Sucesso com `ServiceRequest` isolado (`application/fhir+json`) | `solicitacao-servicerequest-isolado.json` | `Bundle` collection com `DiagnosticReport` e `Observation` | **`200 OK`** | ✅ PASSOU |
| **03** | Rejeição de payload vazio ou malformado | `erro-payload-vazio.json` | `OperationOutcome` (`code: "structure"`) | **`400 Bad Request`** | ✅ PASSOU |
| **04** | Rejeição de recurso incompatível (ex: `Observation` na entrada) | `erro-recurso-incompativel.json` | `OperationOutcome` (`code: "invalid"`) | **`422 Unprocessable`** | ✅ PASSOU |
| **05** | Rejeição de `ServiceRequest` sem código de exame (`code`) | `erro-sem-codigo-exame.json` | `OperationOutcome` (`expression: "ServiceRequest.code"`) | **`422 Unprocessable`** | ✅ PASSOU |
| **06** | Rejeição de `ServiceRequest` sem identificador (`identifier`) | `erro-sem-identificador-pedido.json` | `OperationOutcome` (`expression: "ServiceRequest.identifier"`) | **`422 Unprocessable`** | ✅ PASSOU |
| **07** | Indisponibilidade remota do laboratório (falha HTTP 5xx do lab) | `solicitacao-servicerequest-isolado.json` | `OperationOutcome` (`code: "transient"`) | **`502 Bad Gateway`** | ✅ PASSOU |
| **08** | Timeout na comunicação com o laboratório excedido | `solicitacao-servicerequest-isolado.json` | `OperationOutcome` (`code: "timeout"`) | **`504 Gateway Timeout`** | ✅ PASSOU |
| **09** | Health check operacional do Gateway | N/A (`GET /api/v1/health`) | `HealthStatusDto` (`status: "UP"`, metadados P3) | **`200 OK`** | ✅ PASSOU |
| **10** | Processamento direto do Mock do Laboratório | `pedido-legado.json` | JSON com laudo concluído e protocolo | **`200 OK`** | ✅ PASSOU |
| **11** | Solicitação com múltiplos exames/resultados (1 `DiagnosticReport` + 3 `Observation`s) | `resposta-laboratorio-multiplos-exames.json` | `Bundle` collection com 4 recursos correlacionados | **`200 OK`** | ✅ PASSOU |

---

## 3. Cobertura por Classe de Teste

| Classe de Teste | Quantidade de Testes | Foco Arquitetural | Status |
|:---|:---:|:---|:---:|
| `GatewayScenariosTestSuiteTest` | 11 | Matriz completa de cenários com fixtures JSON | ✅ 11/11 PASSOU |
| `ClinicalGatewayControllerTest` | 7 | Controller clínico REST e tratamento global de erros | ✅ 7/7 PASSOU |
| `LabMockControllerTest` | 5 | Mock do laboratório legado (sucesso, rejeição e simulação de erro) | ✅ 5/5 PASSOU |
| `LabLegacyClientTest` | 4 | Cliente HTTP nativo do Java 21 com timeout de socket e leitura | ✅ 4/4 PASSOU |
| `ClinicalOrchestratorServiceTest` | 1 | Pipeline Pipes and Filters e montagem correlacionada do Bundle | ✅ 1/1 PASSOU |
| `HealthControllerTest` | 1 | Rota operacional de diagnóstico e metadados P3 | ✅ 1/1 PASSOU |
| **TOTAL** | **29** | **Cobertura Integral das Issues API-01 a API-05** | **100% SUCESSO** |

---

## 4. Log Real de Execução do Maven

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running br.ufs.fhirsus.client.LabLegacyClientTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.096 s -- in br.ufs.fhirsus.client.LabLegacyClientTest
[INFO] Running br.ufs.fhirsus.controller.ClinicalGatewayControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.182 s -- in br.ufs.fhirsus.controller.ClinicalGatewayControllerTest
[INFO] Running br.ufs.fhirsus.controller.HealthControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in br.ufs.fhirsus.controller.HealthControllerTest
[INFO] Running br.ufs.fhirsus.mock.LabMockControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.092 s -- in br.ufs.fhirsus.mock.LabMockControllerTest
[INFO] Running br.ufs.fhirsus.scenarios.GatewayScenariosTestSuiteTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.364 s -- in br.ufs.fhirsus.scenarios.GatewayScenariosTestSuiteTest
[INFO] Running br.ufs.fhirsus.service.ClinicalOrchestratorServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in br.ufs.fhirsus.service.ClinicalOrchestratorServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  25.060 s
[INFO] Finished at: 2026-11-22T19:41:04-03:00
[INFO] ------------------------------------------------------------------------
```

---

## 5. Rastreabilidade com Requisitos e Decisões de Arquitetura

* **RF001 / UC-01:** Recepção do pedido de exame, validação semântica e orquestração do ciclo síncrono comprovada nos Cenários 01 e 02.
* **RF002:** Conversão bidirecional FHIR $\leftrightarrow$ JSON Legado comprovada na montagem do laudo com `DiagnosticReport` e `Observation`.
* **RF016:** Envio HTTP com timeout configurável de socket e leitura comprovado no `LabLegacyClientTest` e Cenário 08.
* **CT-01 / CT-05:** Fixtures sintéticas coerentes salvas em `src/test/resources/fixtures/` e utilizadas em testes reproduzíveis.
* **CT-02:** Assinatura `converter(payload, recurso, sentido)` respeitada via `ConversionEngine`.
* **CT-04 / ADR03:** Respostas de erro padronizadas exclusivamente como HL7 FHIR `OperationOutcome` para todos os códigos HTTP (400, 422, 502, 504).
* **ADR09:** Validação rigorosa dos campos obrigatórios HL7 FHIR R4 sem criação de dados forjados ou fallbacks artificiais.
