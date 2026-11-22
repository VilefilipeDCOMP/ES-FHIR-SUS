import type { MapeamentoConfig } from '../types/domain';
import type { ErroValidacao } from '../types/ui';
import type { MappingGateway } from './mappingGateway';

/**
 * contrato provisório (CT-04 — pendente de confirmação com equipe back-end).
 * operação FAIL-CLOSED: nenhuma resposta vazia, ambígua ou fora do contrato
 * é interpretada como sucesso.
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1/mapeamentos';
const ALLOW_HTTP_204_SAVE = import.meta.env.VITE_ALLOW_HTTP_204_SAVE === 'true';

function ehObjetoPlano(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

export async function safeParseJson<T>(res: Response): Promise<T | null> {
  if (res.status === 204) return null;
  const raw = await res.text();
  if (!raw.trim()) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

async function extrairErrosHttp(res: Response): Promise<ErroValidacao[]> {
  const body = await safeParseJson<Record<string, unknown>>(res);
  if (body && Array.isArray(body.erros) && body.erros.length > 0) {
    return body.erros as ErroValidacao[];
  }
  return [
    {
      local: `API (HTTP ${res.status})`,
      motivo:
        typeof body?.message === 'string'
          ? body.message
          : 'Erro retornado pelo servidor ao processar a operação.',
      severidade: 'ERRO',
    },
  ];
}

export const httpMappingApi: MappingGateway = {
  async buscarConfiguracaoAtiva(): Promise<MapeamentoConfig> {
    const res = await fetch(`${API_BASE}/config`);
    if (!res.ok) throw new Error(`Falha ao buscar configuração ativa (HTTP ${res.status}).`);
    const data = await safeParseJson<MapeamentoConfig>(res);
    if (!data) throw new Error('Servidor retornou resposta vazia ao carregar configuração.');
    return data;
  },

  async validarConfiguracao(config) {
    const res = await fetch(`${API_BASE}/validar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    });

    if (!res.ok) {
      return {
        acao: 'VALIDAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Configuração rejeitada pelo validador do servidor.',
        erros: await extrairErrosHttp(res),
        avisos: [],
      };
    }

    const body = await safeParseJson<Record<string, unknown>>(res);
    if (!ehObjetoPlano(body) || typeof body.valido !== 'boolean' || !Array.isArray(body.erros)) {
      return {
        acao: 'VALIDAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Resposta do servidor fora do contrato esperado em /validar.',
        erros: [
          {
            local: 'Contrato HTTP (/validar)',
            motivo: 'Esperado JSON explícito com { valido: boolean, erros: ErroValidacao[] }.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      };
    }

    const erros = body.erros as ErroValidacao[];
    const avisos = Array.isArray(body.avisos) ? (body.avisos as ErroValidacao[]) : [];
    const sucesso = body.valido && erros.length === 0;

    return {
      acao: 'VALIDAR',
      sucesso: sucesso as boolean,
      timestamp: new Date().toISOString(),
      mensagemResumo: sucesso
        ? 'Configuração validada com sucesso pelo servidor.'
        : 'O validador do servidor apontou inconsistências.',
      erros,
      avisos,
    };
  },

  async testarConversao(params) {
    const res = await fetch(`${API_BASE}/testar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params),
    });

    if (!res.ok) {
      return {
        acao: 'TESTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Falha ao executar teste de conversão no motor.',
        erros: await extrairErrosHttp(res),
        avisos: [],
      };
    }

    const body = await safeParseJson<Record<string, unknown>>(res);
    const contratoOk =
      ehObjetoPlano(body) &&
      typeof body.valido === 'boolean' &&
      Array.isArray(body.erros) &&
      (!body.valido || ehObjetoPlano(body.resultado));

    if (!contratoOk) {
      return {
        acao: 'TESTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Resposta do servidor fora do contrato esperado em /testar.',
        erros: [
          {
            local: 'Contrato HTTP (/testar)',
            motivo:
              'Resposta de sucesso exige { valido: true, erros: [], resultado: { ... } }.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      };
    }

    const erros = body!.erros as ErroValidacao[];
    const avisos = Array.isArray(body!.avisos) ? (body!.avisos as ErroValidacao[]) : [];
    const sucesso = body!.valido === true && erros.length === 0;

    return {
      acao: 'TESTAR',
      sucesso,
      timestamp: new Date().toISOString(),
      mensagemResumo: sucesso
        ? 'Teste de conversão concluído com sucesso pelo motor.'
        : 'Teste retornou erros de conversão.',
      erros,
      avisos,
      payloadSaida: ehObjetoPlano(body!.resultado) ? body!.resultado as Record<string, unknown> : undefined,
    };
  },

  async salvarConfiguracao(config) {
    const res = await fetch(`${API_BASE}/config`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    });

    if (!res.ok) {
      return {
        acao: 'SALVAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Falha ao persistir configuração. A configuração anterior permanece ativa.',
        erros: await extrairErrosHttp(res),
        avisos: [],
      };
    }

    if (res.status === 204) {
      if (!ALLOW_HTTP_204_SAVE) {
        return {
          acao: 'SALVAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo:
            'HTTP 204 recebido sem autorização no contrato CT-04. Salvamento não confirmado.',
          erros: [
            {
              local: 'Contrato HTTP (PUT /config)',
              motivo:
                'Confirme com o back-end se 204 significa persistência+ativação ou habilite VITE_ALLOW_HTTP_204_SAVE=true.',
              severidade: 'ERRO',
            },
          ],
          avisos: [],
        };
      }

      return {
        acao: 'SALVAR',
        sucesso: true,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Configuração salva e ativada no servidor (HTTP 204).',
        erros: [],
        avisos: [],
      };
    }

    const body = await safeParseJson<Record<string, unknown>>(res);
    const erros = ehObjetoPlano(body) && Array.isArray(body.erros)
      ? (body.erros as ErroValidacao[])
      : [];
    const confirmacaoExplicita =
      ehObjetoPlano(body) &&
      (body.sucesso === true || body.valido === true) &&
      erros.length === 0;

    if (!confirmacaoExplicita) {
      return {
        acao: 'SALVAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Servidor não confirmou explicitamente a persistência. A versão anterior permanece ativa.',
        erros:
          erros.length > 0
            ? erros
            : [
                {
                  local: 'Contrato HTTP (PUT /config)',
                  motivo: 'Resposta 2xx sem confirmação explícita { sucesso: true }.',
                  severidade: 'ERRO',
                },
              ],
        avisos: [],
      };
    }

    return {
      acao: 'SALVAR',
      sucesso: true,
      timestamp: new Date().toISOString(),
      mensagemResumo: 'Configuração salva e ativada no servidor.',
      erros: [],
      avisos: Array.isArray(body!.avisos) ? (body!.avisos as ErroValidacao[]) : [],
    };
  },
};
