import { z } from 'zod';
import {
  RECURSOS_OFICIAIS_FHIR,
  type MapeamentoConfig,
  type RecursoFhir,
} from '../types/domain';
import type { CampoFhirSpec, ErroValidacao } from '../types/ui';
import { avaliarPendenciasRecurso } from './mappingValidation';

const CAMINHO_SIMPLES_REGEX = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/;
const REGRA_MOTOR_REGEX = /^(COPIAR|TABELA:[A-Za-z0-9_-]+)$/;
const NOME_TABELA_REGEX = /^[A-Za-z0-9_-]+$/;

const RegraCampoSchema = z.object({
  caminhoOrigem: z
    .string()
    .trim()
    .min(1, 'caminhoOrigem é obrigatório.')
    .regex(CAMINHO_SIMPLES_REGEX, 'Apenas caminhos simples separados por ponto são suportados (DT05).'),
  caminhoDestino: z
    .string()
    .trim()
    .min(1, 'caminhoDestino é obrigatório.')
    .regex(CAMINHO_SIMPLES_REGEX, 'Apenas caminhos simples separados por ponto são suportados (DT05).'),
  regra: z
    .string()
    .regex(REGRA_MOTOR_REGEX, 'Regra não suportada pelo motor: permitido apenas COPIAR ou TABELA:<nome>.'),
  obrigatorio: z.boolean(),
});

const SentidoSchema = z.object({
  tipo: z.enum(['LEGADO_PARA_FHIR', 'FHIR_PARA_LEGADO']),
  regras: z.array(RegraCampoSchema),
});

const MapeamentoSchema = z.object({
  recurso: z.enum(RECURSOS_OFICIAIS_FHIR),
  sentidos: z
    .array(SentidoSchema)
    .min(1, 'Cada recurso deve possuir ao menos 1 sentido.')
    .max(2, 'Cada recurso pode possuir no máximo 2 sentidos.'),
});

const TabelaSchema = z.object({
  nome: z
    .string()
    .trim()
    .min(1, 'Nome da tabela é obrigatório.')
    .regex(NOME_TABELA_REGEX, 'Nome de tabela deve conter apenas letras, números, "_" ou "-".'),
  equivalencia: z
    .record(z.string().trim().min(1), z.string().trim().min(1))
    .refine(
      (eq) => Object.keys(eq).length > 0,
      'A tabela de equivalência não pode estar vazia.'
    ),
});

export const MapeamentoConfigSchema = z.object({
  mapeamentos: z.array(MapeamentoSchema).min(1, 'Informe ao menos um recurso em "mapeamentos".'),
  tabelas: z.array(TabelaSchema),
});

export interface ResultadoValidacaoEsquema {
  ok: boolean;
  config?: MapeamentoConfig;
  erros: ErroValidacao[];
  avisos: ErroValidacao[];
}

/**
 * validação completa em tempo de execução (RF012):
 * 1. valida estrutura, tipos, enums e ausência de nulos via Zod.
 * 2. valida invariantes semânticas (recursos únicos, sentidos únicos, destinos únicos,
 *    prefixo FHIR, existência no catálogo, tabelas referenciadas e obrigatoriedades).
 */
export function validarConfiguracaoDesconhecida(
  input: unknown,
  catalogo: Record<RecursoFhir, CampoFhirSpec[]>
): ResultadoValidacaoEsquema {
  const parsed = MapeamentoConfigSchema.safeParse(input);
  if (!parsed.success) {
    return {
      ok: false,
      erros: parsed.error.issues.map((issue) => ({
        local: issue.path.length > 0 ? issue.path.join('.') : 'MapeamentoConfig (raiz)',
        motivo: issue.message,
        severidade: 'ERRO',
      })),
      avisos: [],
    };
  }

  const config = parsed.data as MapeamentoConfig;
  const erros: ErroValidacao[] = [];
  const avisos: ErroValidacao[] = [];

  // 1. nomes de tabelas únicos
  const nomesTabelas = new Set<string>();
  for (const tab of config.tabelas) {
    if (nomesTabelas.has(tab.nome)) {
      erros.push({
        local: `tabelas.${tab.nome}`,
        motivo: `Nome de tabela duplicado: "${tab.nome}".`,
        severidade: 'ERRO',
      });
    }
    nomesTabelas.add(tab.nome);
  }

  // 2. recursos únicos, sentidos únicos, destinos únicos, catálogo e obrigatoriedades
  const recursosVistos = new Set<RecursoFhir>();
  for (const map of config.mapeamentos) {
    if (recursosVistos.has(map.recurso)) {
      erros.push({
        local: map.recurso,
        motivo: `Recurso "${map.recurso}" declarado mais de uma vez.`,
        severidade: 'ERRO',
      });
    }
    recursosVistos.add(map.recurso);

    const spec = catalogo[map.recurso] ?? [];
    const caminhosCatalogo = new Set(spec.map((c) => c.caminho));
    const sentidosVistos = new Set<string>();

    for (const sentido of map.sentidos) {
      if (sentidosVistos.has(sentido.tipo)) {
        erros.push({
          local: `${map.recurso} > ${sentido.tipo}`,
          motivo: `Sentido "${sentido.tipo}" duplicado no mesmo recurso.`,
          severidade: 'ERRO',
        });
      }
      sentidosVistos.add(sentido.tipo);

      if (sentido.regras.length === 0) {
        erros.push({
          local: `${map.recurso} > ${sentido.tipo}`,
          motivo: 'Sentido declarado sem nenhuma regra de mapeamento.',
          severidade: 'ERRO',
        });
      }

      const destinosVistos = new Set<string>();
      for (const regra of sentido.regras) {
        if (destinosVistos.has(regra.caminhoDestino)) {
          erros.push({
            local: `${map.recurso} > ${sentido.tipo} > ${regra.caminhoDestino}`,
            motivo: 'Mais de uma regra aponta para o mesmo campo de destino.',
            severidade: 'ERRO',
          });
        }
        destinosVistos.add(regra.caminhoDestino);

        const campoFhir =
          sentido.tipo === 'LEGADO_PARA_FHIR' ? regra.caminhoDestino : regra.caminhoOrigem;

        if (caminhosCatalogo.size > 0 && !caminhosCatalogo.has(campoFhir)) {
          erros.push({
            local: `${map.recurso} > ${sentido.tipo} > ${campoFhir}`,
            motivo: `Campo "${campoFhir}" não existe no catálogo suportado de ${map.recurso}.`,
            severidade: 'ERRO',
          });
        }
      }

      const resumo = avaliarPendenciasRecurso(config, map.recurso, sentido.tipo, spec);
      erros.push(...resumo.errosUx);
      avisos.push(...resumo.avisosUx);
    }
  }

  return erros.length > 0
    ? { ok: false, erros, avisos }
    : { ok: true, config, erros: [], avisos };
}
