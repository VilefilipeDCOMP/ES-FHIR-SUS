import type { MapeamentoConfig, RecursoFhir } from '../types/domain';

/** payloads 100% sintéticos — alinhados a fhirsus/src/test/resources/mapeamento-p3-sintetico.json */
export const EXEMPLO_LEGADO_SINTETICO: Record<string, unknown> = {
  numeroPedido: 'PED-001',
  codigoExame: 'LAB-042',
  origem: 'CLINICA_DEMO',
  situacao: 'LIBERADO',
  resultado: 92.4,
  paciente: {
    nome: 'PACIENTE SINTETICO EXEMPLO',
    sexo: 'M',
  },
};

export const EXEMPLOS_FHIR_SINTETICOS: Partial<Record<RecursoFhir, Record<string, unknown>>> = {
  ServiceRequest: {
    resourceType: 'ServiceRequest',
    identifier: [
      {
        system: 'https://clinica.example/identificadores/pedidos',
        value: 'PED-001',
      },
    ],
    status: 'active',
    intent: 'order',
    code: {
      coding: [
        {
          system: 'https://clinica.example/codigos/exames',
          code: 'EXAME-001',
        },
      ],
      text: 'Glicose serica',
    },
    subject: {
      reference: 'urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2',
      display: 'PACIENTE SINTETICO EXEMPLO',
    },
  },
  Observation: {
    resourceType: 'Observation',
    status: 'final',
    code: {
      coding: [
        {
          system: 'https://clinica.example/codigos/exames',
          code: 'EXAME-001',
        },
      ],
      text: 'Glicose',
    },
    valueQuantity: { value: 92.4, unit: 'mg/dL' },
  },
  DiagnosticReport: {
    resourceType: 'DiagnosticReport',
    status: 'final',
    code: {
      coding: [{ system: 'http://loinc.org', code: '24323-8' }],
      text: 'Painel Glicemia',
    },
    subject: { reference: 'Patient/DEMO-0001' },
    conclusion: 'Laudo sintetico de demonstracao.',
  },
  Patient: {
    resourceType: 'Patient',
    identifier: [{ value: 'PAC-001' }],
    name: [{ text: 'PACIENTE SINTETICO EXEMPLO' }],
    gender: 'male',
    birthDate: '1990-01-15',
  },
};

/**
 * configuração sintética oficial idêntica à fixture fhirsus/src/test/resources/mapeamento-p3-sintetico.json
 */
export const CONFIG_INICIAL_SINTETICA: MapeamentoConfig = {
  mapeamentos: [
    {
      recurso: 'ServiceRequest',
      toLegado: [
        {
          caminhoOrigem: '$.identifier[0].value',
          caminhoDestino: 'numeroPedido',
          tipoConversao: 'DIRETA',
          parametroConversao: null,
          obrigatorio: true,
        },
        {
          caminhoOrigem: '$.code.coding[0].code',
          caminhoDestino: 'codigoExame',
          tipoConversao: 'DOMINIO',
          parametroConversao: 'exames_clinica_para_lab',
          obrigatorio: true,
        },
        {
          caminhoDestino: 'origem',
          tipoConversao: 'CONSTANTE',
          parametroConversao: 'CLINICA_DEMO',
          obrigatorio: true,
        },
      ],
      toFHIR: [],
    },
    {
      recurso: 'Observation',
      toLegado: [
        {
          caminhoOrigem: '$.code.coding[0].code',
          caminhoDestino: 'codigoExame',
          tipoConversao: 'DOMINIO',
          parametroConversao: 'exames_clinica_para_lab',
          obrigatorio: true,
        },
      ],
      toFHIR: [
        {
          caminhoDestino: 'resourceType',
          tipoConversao: 'CONSTANTE',
          parametroConversao: 'Observation',
          obrigatorio: true,
        },
        {
          caminhoDestino: 'code.coding[0].system',
          tipoConversao: 'CONSTANTE',
          parametroConversao: 'https://clinica.example/codigos/exames',
          obrigatorio: true,
        },
        {
          caminhoOrigem: '$.codigoExame',
          caminhoDestino: 'code.coding[0].code',
          tipoConversao: 'DOMINIO',
          parametroConversao: 'exames_lab_para_clinica',
          obrigatorio: true,
        },
        {
          caminhoOrigem: '$.situacao',
          caminhoDestino: 'status',
          tipoConversao: 'DOMINIO',
          parametroConversao: 'status_lab_para_fhir',
          obrigatorio: true,
        },
        {
          caminhoOrigem: '$.resultado',
          caminhoDestino: 'valueQuantity.value',
          tipoConversao: 'DIRETA',
          parametroConversao: null,
          obrigatorio: true,
        },
      ],
    },
  ],
  dominios: [
    {
      nome: 'exames_clinica_para_lab',
      equivalencia: { 'EXAME-001': 'LAB-042' },
    },
    {
      nome: 'exames_lab_para_clinica',
      equivalencia: { 'LAB-042': 'EXAME-001' },
    },
    {
      nome: 'status_lab_para_fhir',
      equivalencia: { LIBERADO: 'final' },
    },
  ],
};
