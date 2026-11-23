import type { RecursoFhir } from '../types/domain';
import type { CampoFhirSpec } from '../types/ui';

/** indica se o campo participa de alguma exigência obrigatória (direta ou por grupo FHIR R4) */
export function participaDeObrigatoriedade(campo: CampoFhirSpec): boolean {
  return (
    campo.origemObrigatoriedade === 'FHIR_R4' ||
    campo.origemObrigatoriedade === 'REGRA_PROJETO' ||
    Boolean(campo.grupoObrigatorioFhir)
  );
}

/**
 * normaliza um caminho FHIR (seja origem JSONPath "$.code.coding[0].code",
 * destino Dot Notation "code.coding[0].code" ou prefixado "ServiceRequest.code.coding.code")
 * para o formato canônico do catálogo relativo ao recurso.
 */
export function normalizarCaminhoFhirParaCatalogo(
  recurso: RecursoFhir,
  caminhoBruto: string | undefined
): string {
  if (!caminhoBruto) return '';
  let limpo = caminhoBruto.trim();
  if (limpo.startsWith('$.')) {
    limpo = limpo.slice(2);
  } else if (limpo === '$') {
    return '$';
  }
  if (limpo.startsWith(`${recurso}.`)) {
    limpo = limpo.slice(recurso.length + 1);
  }
  return limpo;
}

/**
 * catálogo de UX alinhado ao HL7 FHIR R4 v4.0.1 e à sintaxe de CaminhoJson do motor:
 * - caminhos relativos ao recurso com índices explícitos [0] para coleções (0..*).
 * - na origem (toLegado), o editor prefixa com "$." (ex.: "$.identifier[0].value").
 * - no destino (toFHIR), o editor usa Dot Notation direta (ex.: "code.coding[0].code").
 */
export const FHIR_CATALOG: Record<RecursoFhir, CampoFhirSpec[]> = {
  ServiceRequest: [
    {
      caminho: 'resourceType',
      descricao: 'Tipo do recurso FHIR R4 ("ServiceRequest")',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'identifier[0].value',
      descricao: 'Identificador do pedido clínico (ex.: PED-001)',
      tipo: 'string',
      origemObrigatoriedade: 'REGRA_PROJETO',
      colecaoFhir: true,
    },
    {
      caminho: 'status',
      descricao: 'Situação do pedido (1..1 no FHIR R4: active | completed | revoked)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'intent',
      descricao: 'Intenção do pedido (1..1 no FHIR R4: order | proposal | plan)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'code.coding[0].system',
      descricao: 'Sistema de codificação do exame solicitado',
      tipo: 'uri',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'code.coding[0].code',
      descricao: 'Código do exame solicitado (exigido pelo fluxo laboratorial)',
      tipo: 'code',
      origemObrigatoriedade: 'REGRA_PROJETO',
      colecaoFhir: true,
    },
    {
      caminho: 'code.text',
      descricao: 'Descrição textual do exame solicitado',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'subject.reference',
      descricao: 'Referência ao paciente (satisfaz ServiceRequest.subject 1..1 no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'ServiceRequest.subject',
    },
    {
      caminho: 'subject.display',
      descricao: 'Descrição textual do paciente (alternativa válida para ServiceRequest.subject)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'ServiceRequest.subject',
    },
    {
      caminho: 'authoredOn',
      descricao: 'Data/hora sintética de emissão do pedido',
      tipo: 'dateTime',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Observation: [
    {
      caminho: 'resourceType',
      descricao: 'Tipo do recurso FHIR R4 ("Observation")',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'status',
      descricao: 'Situação da observação (1..1 no FHIR R4: registered | preliminary | final)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'code.coding[0].system',
      descricao: 'Sistema de codificação do exame/analito',
      tipo: 'uri',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'code.coding[0].code',
      descricao: 'Código do analito/exame (satisfaz Observation.code 1..1 no FHIR R4)',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'Observation.code',
      colecaoFhir: true,
    },
    {
      caminho: 'code.text',
      descricao: 'Descrição textual do analito (alternativa válida para Observation.code)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'Observation.code',
    },
    {
      caminho: 'valueQuantity.value',
      descricao: 'Valor numérico do resultado laboratorial',
      tipo: 'decimal',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'valueQuantity.unit',
      descricao: 'Unidade de medida (ex.: mg/dL)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  DiagnosticReport: [
    {
      caminho: 'resourceType',
      descricao: 'Tipo do recurso FHIR R4 ("DiagnosticReport")',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'status',
      descricao: 'Situação do laudo (1..1 no FHIR R4: registered | preliminary | final)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'code.coding[0].code',
      descricao: 'Código do painel diagnóstico (satisfaz DiagnosticReport.code 1..1 no FHIR R4)',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'DiagnosticReport.code',
      colecaoFhir: true,
    },
    {
      caminho: 'code.text',
      descricao: 'Nome textual do painel (alternativa válida para DiagnosticReport.code)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'DiagnosticReport.code',
    },
    {
      caminho: 'subject.reference',
      descricao: 'Referência ao paciente (exigido pelo fluxo do projeto)',
      tipo: 'string',
      origemObrigatoriedade: 'REGRA_PROJETO',
    },
    {
      caminho: 'conclusion',
      descricao: 'Conclusão textual sintética do laudo',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Patient: [
    {
      caminho: 'resourceType',
      descricao: 'Tipo do recurso FHIR R4 ("Patient")',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'identifier[0].value',
      descricao: 'Identificador sintético (exigido por regra do projeto)',
      tipo: 'string',
      origemObrigatoriedade: 'REGRA_PROJETO',
      colecaoFhir: true,
    },
    {
      caminho: 'name[0].text',
      descricao: 'Nome completo sintético (0..* no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'gender',
      descricao: 'Sexo administrativo: male | female | other | unknown (0..1 no FHIR R4)',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'birthDate',
      descricao: 'Data de nascimento sintética AAAA-MM-DD (0..1 no FHIR R4)',
      tipo: 'date',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'active',
      descricao: 'Indica se o registro do paciente está ativo',
      tipo: 'boolean',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Specimen: [
    {
      caminho: 'identifier[0].value',
      descricao: 'Código sintético da amostra (0..* no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'type.text',
      descricao: 'Tipo de material biológico (0..1 no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Practitioner: [
    {
      caminho: 'identifier[0].value',
      descricao: 'Identificador sintético do profissional',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'name[0].text',
      descricao: 'Nome sintético do profissional',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
  ],
  PractitionerRole: [
    {
      caminho: 'code[0].text',
      descricao: 'Papel desempenhado pelo profissional',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
  ],
  Organization: [
    {
      caminho: 'identifier[0].value',
      descricao: 'Identificador sintético da organização',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'name',
      descricao: 'Nome sintético da organização',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
};
