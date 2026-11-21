/** regras efetivamente suportadas pelo motor no P3 (DT05) */
export type TipoRegraMotor = 'COPIAR' | 'TABELA';

/** opções visíveis no protótipo; as 2 últimas ficam desabilitadas ("planejado") */
export type TipoRegraUI = TipoRegraMotor | 'FORMATO_DATA' | 'VALOR_PADRAO';

/** expressão estaticamente restrita às regras que o motor executa */
export type ExpressaoRegraMotor = 'COPIAR' | `TABELA:${string}`;

export type SentidoTipo = 'LEGADO_PARA_FHIR' | 'FHIR_PARA_LEGADO';

/**
 * lista oficial dos 8 recursos definidos no P1 (D03) e no P2.
 * Encounter não faz parte do projeto; o oitavo recurso é PractitionerRole.
 */
export const RECURSOS_OFICIAIS_FHIR = [
  'ServiceRequest',
  'Patient',
  'Practitioner',
  'PractitionerRole',
  'Organization',
  'DiagnosticReport',
  'Observation',
  'Specimen',
] as const;

export type RecursoFhir = (typeof RECURSOS_OFICIAIS_FHIR)[number];

/**
 * recorte demonstrável no P3 (DT05) alinhado ao fluxo laboratorial.
 * [suposição CT-01: confirmar com a equipe quais recursos o motor executa nesta entrega]
 */
export const RECURSOS_ATIVOS_P3: readonly RecursoFhir[] = [
  'ServiceRequest',
  'DiagnosticReport',
  'Observation',
  'Patient',
] as const;

export interface RegraCampo {
  caminhoOrigem: string;
  caminhoDestino: string;
  regra: ExpressaoRegraMotor;
  obrigatorio: boolean;
}

export interface Sentido {
  /** [suposição CT-03: confirmar nome exato da propriedade no Jackson] */
  tipo: SentidoTipo;
  regras: RegraCampo[];
}

export interface Mapeamento {
  recurso: RecursoFhir;
  /** de 1 a 2 sentidos (Legado->FHIR e/ou FHIR->Legado) */
  sentidos: Sentido[];
}

export interface Tabela {
  nome: string;
  /** Map<valorOrigem, valorDestino> */
  equivalencia: Record<string, string>;
}

export interface MapeamentoConfig {
  mapeamentos: Mapeamento[];
  tabelas: Tabela[];
}
