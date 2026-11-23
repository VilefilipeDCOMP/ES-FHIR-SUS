# Relatório de Evidências de Testes — API / Gateway (P3)
**Projeto:** FHIR SUS+ · **Recorte:** Projeto Integrador P3  
**Frente:** API / Gateway · **Responsável:** Bruno Carvalho Santana Rocha (@BrunocsRocha)  
**Data da Execução:** 22/11/2026 · **Ambiente:** Java 21 / Spring Boot 4.0.10 / Maven 3.9  

---

## 1. Resumo Executivo da Suíte de Testes

A suíte automatizada de testes do Gateway foi construída para cobrir a matriz completa de cenários de sucesso, validação sintática e semântica, resiliência de rede e conformidade com os contratos HL7 FHIR R4 acordados (CT-01, CT-02, CT-04, CT-05, ADR03, ADR09 e UC-01 do P2).

Após o merge de integração com as frentes dos colegas (Danilo Campos, Luiz Manoel e Filipe Ciríaco), a suíte completa conta com o adaptador oficial `MotorConversaoAdapter`, conectando o Gateway ao motor de regras em memória, ao validador HAPI FHIR R4 e ao versionamento em PostgreSQL.

* **Total de Testes do Gateway e Adaptação:** 35 testes
* **Total da Suíte Integrada do Projeto (Todos os Integrantes):** 97 testes
* **Total de Falhas / Erros:** 0 falhas / 0 erros
* **Taxa de Sucesso:** 100% (`BUILD SUCCESS`)
* **Tempo de Execução da Suíte Completa:** ~1 min 12 s

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

## 3. Cobertura por Classe de Teste da Frente Gateway

| Classe de Teste | Quantidade de Testes | Foco Arquitetural | Status |
|:---|:---:|:---|:---:|
| `GatewayScenariosTestSuiteTest` | 11 | Matriz completa de cenários com fixtures JSON | ✅ 11/11 PASSOU |
| `ClinicalGatewayControllerTest` | 7 | Controller clínico REST e tratamento global de erros | ✅ 7/7 PASSOU |
| `LabMockControllerTest` | 5 | Mock do laboratório legado (sucesso, rejeição e simulação de erro) | ✅ 5/5 PASSOU |
| `MotorConversaoAdapterTest` | 5 | Adaptador do motor oficial, integração com PostgreSQL e fallback | ✅ 5/5 PASSOU |
| `LabLegacyClientTest` | 4 | Cliente HTTP nativo do Java 21 com timeout de socket e leitura | ✅ 4/4 PASSOU |
| `ClinicalOrchestratorServiceTest` | 1 | Pipeline Pipes and Filters e montagem correlacionada do Bundle | ✅ 1/1 PASSOU |
| `PipelineFiltersInspectionTest` | 1 | Inspeção detalhada passo a passo dos 5 filtros do pipeline | ✅ 1/1 PASSOU |
| `HealthControllerTest` | 1 | Rota operacional de diagnóstico e metadados P3 | ✅ 1/1 PASSOU |
| **TOTAL GATEWAY** | **35** | **Cobertura Integral das Issues API-01 a API-05 + Adaptação** | **100% SUCESSO** |

---

## 4. Cobertura da Suíte Completa Integrada (Todos os Integrantes)

| Módulo / Frente | Responsável | Classes Testadas | Quantidade de Testes | Status |
|:---|:---|:---|:---:|:---:|
| **API / Gateway** | Bruno Rocha | `ClinicalGatewayController`, `ClinicalOrchestratorService`, `LabLegacyClient`, `LabMockController`, `HealthController`, `MotorConversaoAdapter` | 35 | ✅ 35/35 PASSOU |
| **Motor de Conversão** | Luiz Manoel | `MotorConversao`, `CaminhoJson`, `DominioValores`, `ValidacaoCaminhos`, `SaidaJson` | 21 | ✅ 21/21 PASSOU |
| **Configuração & Banco** | Danilo Campos | `ConfigController`, `ConfigService`, `ConfigValidator`, `ConfigParser` | 26 | ✅ 26/26 PASSOU |
| **Validação FHIR R4** | Filipe Ciríaco | `ValidadorFhir` (HAPI FHIR R4 Validator) | 14 | ✅ 14/14 PASSOU |
| **Aplicação** | Geral | `FhirsusApplicationTests` (Carregamento do Contexto Spring Boot) | 1 | ✅ 1/1 PASSOU |
| **TOTAL GERAL** | **Equipe FHIR SUS+** | **46 classes de produção** | **97** | **100% SUCESSO** |

---

## 5. Log Real de Execução do Maven

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running br.ufs.fhirsus.client.LabLegacyClientTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.237 s -- in br.ufs.fhirsus.client.LabLegacyClientTest
[INFO] Running br.ufs.fhirsus.config.ConfigControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.781 s -- in br.ufs.fhirsus.config.ConfigControllerTest
[INFO] Running br.ufs.fhirsus.config.ConfigParserTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.089 s -- in br.ufs.fhirsus.config.ConfigParserTest
[INFO] Running br.ufs.fhirsus.config.ConfigServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.176 s -- in br.ufs.fhirsus.config.ConfigServiceTest
[INFO] Running br.ufs.fhirsus.config.ConfigValidatorTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.046 s -- in br.ufs.fhirsus.config.ConfigValidatorTest
[INFO] Running br.ufs.fhirsus.controller.ClinicalGatewayControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.158 s -- in br.ufs.fhirsus.controller.ClinicalGatewayControllerTest
[INFO] Running br.ufs.fhirsus.controller.HealthControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.016 s -- in br.ufs.fhirsus.controller.HealthControllerTest
[INFO] Running br.ufs.fhirsus.engine.MotorConversaoAdapterTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.203 s -- in br.ufs.fhirsus.engine.MotorConversaoAdapterTest
[INFO] Running br.ufs.fhirsus.FhirsusApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.298 s -- in br.ufs.fhirsus.FhirsusApplicationTests
[INFO] Running br.ufs.fhirsus.map.MotorConversaoTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.197 s -- in br.ufs.fhirsus.map.MotorConversaoTest
[INFO] Running br.ufs.fhirsus.mock.LabMockControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.033 s -- in br.ufs.fhirsus.mock.LabMockControllerTest
[INFO] Running br.ufs.fhirsus.scenarios.GatewayScenariosTestSuiteTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.183 s -- in br.ufs.fhirsus.scenarios.GatewayScenariosTestSuiteTest
[INFO] Running br.ufs.fhirsus.scenarios.PipelineFiltersInspectionTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in br.ufs.fhirsus.scenarios.PipelineFiltersInspectionTest
[INFO] Running br.ufs.fhirsus.service.ClinicalOrchestratorServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in br.ufs.fhirsus.service.ClinicalOrchestratorServiceTest
[INFO] Running br.ufs.fhirsus.validacao.ValidadorFhirTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 25.50 s -- in br.ufs.fhirsus.validacao.ValidadorFhirTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 97, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
