import type React from 'react';
import { useEffect, useState } from 'react';
import type { RecursoFhir, SentidoTipo } from '../../types/domain';
import type { CampoLegado } from '../../types/ui';
import {
  EXEMPLOS_FHIR_SINTETICOS,
  EXEMPLO_LEGADO_SINTETICO,
} from '../../data/syntheticExamples';
import {
  lerArquivoJson,
  type ResultadoIngestao,
  validarEProcessarJsonTexto,
} from '../../utils/jsonIngest';

export type ModoCargaJson = 'MOLDE_LEGADO' | 'EXEMPLO_FHIR_TESTE';

interface Props {
  aberto: boolean;
  recursoAtual: RecursoFhir;
  sentidoAtual: SentidoTipo;
  onFechar: () => void;
  onConcluirMoldeLegado: (campos: CampoLegado[], objetoBruto: Record<string, unknown>) => void;
  onConcluirExemploFhir: (objetoFhir: Record<string, unknown>) => void;
}

export const LoadJsonModal: React.FC<Props> = ({
  aberto,
  recursoAtual,
  sentidoAtual,
  onFechar,
  onConcluirMoldeLegado,
  onConcluirExemploFhir,
}) => {
  const [modo, setModo] = useState<ModoCargaJson>('MOLDE_LEGADO');
  const [passo, setPasso] = useState<1 | 2>(1);
  const [aba, setAba] = useState<'ARQUIVO' | 'COLAR'>('ARQUIVO');
  const [textoColado, setTextoColado] = useState('');
  const [resultado, setResultado] = useState<ResultadoIngestao | null>(null);

  useEffect(() => {
    if (aberto) {
      setModo(sentidoAtual === 'FHIR_PARA_LEGADO' ? 'EXEMPLO_FHIR_TESTE' : 'MOLDE_LEGADO');
    }
  }, [aberto, sentidoAtual]);

  if (!aberto) return null;

  const resetarEFechar = () => {
    setPasso(1);
    setResultado(null);
    setTextoColado('');
    onFechar();
  };

  const handleArquivo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setResultado(await lerArquivoJson(file));
  };

  const handleUsarExemplo = () => {
    const payload =
      modo === 'EXEMPLO_FHIR_TESTE'
        ? (EXEMPLOS_FHIR_SINTETICOS[recursoAtual] ?? EXEMPLOS_FHIR_SINTETICOS.ServiceRequest)
        : EXEMPLO_LEGADO_SINTETICO;

    const jsonTexto = JSON.stringify(payload, null, 2);
    setAba('COLAR');
    setTextoColado(jsonTexto);
    setResultado(
      validarEProcessarJsonTexto(
        jsonTexto,
        modo === 'EXEMPLO_FHIR_TESTE'
          ? `Exemplo FHIR R4 (${recursoAtual})`
          : 'Molde Legado Sintético'
      )
    );
  };

  const podeAvancar = Boolean(resultado?.sucesso && resultado.objetoBruto);

  return (
    <div className="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl max-w-2xl w-full shadow-xl overflow-hidden">
        <div className="p-4 border-b flex items-center justify-between gap-2">
          <div>
            <h2 className="text-base font-semibold text-slate-900">
              Carregar JSON de origem (UC-02)
            </h2>
            <p className="text-xs text-slate-500">
              Passo {passo} de 2 — {passo === 1 ? 'Selecionar origem' : 'Conferir campos'}
            </p>
          </div>
          <button
            type="button"
            onClick={handleUsarExemplo}
            className="text-xs px-2.5 py-1 rounded border border-blue-200 text-blue-700 hover:bg-blue-50"
          >
            Usar exemplo ({modo === 'EXEMPLO_FHIR_TESTE' ? `FHIR ${recursoAtual}` : 'Legado'})
          </button>
        </div>

        <div className="p-4 space-y-4">
          {passo === 1 ? (
            <>
              {/* Seletor explícito para nunca misturar campos Legados com payload FHIR */}
              <div className="flex flex-wrap gap-2 text-xs">
                <button
                  type="button"
                  onClick={() => {
                    setModo('MOLDE_LEGADO');
                    setResultado(null);
                  }}
                  className={`px-3 py-1.5 rounded border ${
                    modo === 'MOLDE_LEGADO'
                      ? 'bg-blue-50 border-blue-500 text-blue-800 font-semibold'
                      : 'bg-white text-slate-600'
                  }`}
                >
                  JSON Legado (preenche coluna Legado + teste Legado→FHIR)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setModo('EXEMPLO_FHIR_TESTE');
                    setResultado(null);
                  }}
                  className={`px-3 py-1.5 rounded border ${
                    modo === 'EXEMPLO_FHIR_TESTE'
                      ? 'bg-blue-50 border-blue-500 text-blue-800 font-semibold'
                      : 'bg-white text-slate-600'
                  }`}
                >
                  Exemplo FHIR R4 (apenas teste FHIR→Legado)
                </button>
              </div>

              <div className="flex gap-2 border-b pb-2 text-xs">
                <button
                  type="button"
                  onClick={() => {
                    setAba('ARQUIVO');
                    setResultado(null);
                  }}
                  className={`px-3 py-1.5 rounded ${
                    aba === 'ARQUIVO' ? 'bg-slate-900 text-white' : 'bg-slate-100'
                  }`}
                >
                  Enviar arquivo
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setAba('COLAR');
                    setResultado(null);
                  }}
                  className={`px-3 py-1.5 rounded ${
                    aba === 'COLAR' ? 'bg-slate-900 text-white' : 'bg-slate-100'
                  }`}
                >
                  Colar JSON
                </button>
              </div>

              {aba === 'ARQUIVO' ? (
                <div className="border-2 border-dashed rounded-lg p-6 text-center">
                  <input
                    type="file"
                    accept=".json,application/json"
                    onChange={handleArquivo}
                    className="text-xs"
                  />
                  <p className="text-xs text-slate-500 mt-2">
                    Limite máximo: 5 MB. Utilize exclusivamente dados sintéticos.
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  <textarea
                    rows={8}
                    aria-label="Conteúdo JSON"
                    value={textoColado}
                    onChange={(e) => {
                      setTextoColado(e.target.value);
                      setResultado(null);
                    }}
                    placeholder='{ "pedido": { "status_legado": "ATIVO" } }'
                    className="w-full p-2.5 border rounded font-mono text-xs"
                  />
                  <button
                    type="button"
                    onClick={() =>
                      setResultado(validarEProcessarJsonTexto(textoColado, 'JSON colado'))
                    }
                    className="text-xs px-3 py-1.5 rounded bg-slate-800 text-white"
                  >
                    Validar JSON
                  </button>
                </div>
              )}

              {resultado && !resultado.sucesso && (
                <div role="alert" className="bg-red-50 border border-red-200 rounded p-3 space-y-1">
                  {resultado.erros.map((err, i) => (
                    <p key={i} className="text-xs text-red-800">
                      <strong>{err.local}:</strong> {err.motivo}
                    </p>
                  ))}
                </div>
              )}

              {resultado?.sucesso && (
                <p className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded p-2.5">
                  JSON válido: {resultado.campos.length} campos simples detectados.
                </p>
              )}
            </>
          ) : (
            <div className="max-h-72 overflow-y-auto border rounded divide-y text-xs">
              {resultado?.campos.map((c) => (
                <div key={c.caminho} className="p-2 flex justify-between">
                  <span className="font-mono font-semibold">{c.caminho}</span>
                  <span className="text-slate-500">
                    {c.valorExemplo} ({c.tipo})
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="p-4 border-t bg-slate-50 flex justify-end gap-2">
          <button
            type="button"
            onClick={resetarEFechar}
            className="px-3 py-1.5 text-xs border rounded bg-white"
          >
            Cancelar
          </button>
          {passo === 1 ? (
            <button
              type="button"
              disabled={!podeAvancar}
              onClick={() => setPasso(2)}
              className="px-3 py-1.5 text-xs rounded bg-blue-600 text-white disabled:opacity-40"
            >
              Avançar
            </button>
          ) : (
            <button
              type="button"
              onClick={() => {
                if (resultado?.sucesso && resultado.objetoBruto) {
                  if (modo === 'MOLDE_LEGADO') {
                    onConcluirMoldeLegado(resultado.campos, resultado.objetoBruto);
                  } else {
                    onConcluirExemploFhir(resultado.objetoBruto);
                  }
                  resetarEFechar();
                }
              }}
              className="px-3 py-1.5 text-xs rounded bg-emerald-600 text-white"
            >
              {modo === 'MOLDE_LEGADO' ? 'Carregar campos legados' : 'Carregar exemplo FHIR'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
