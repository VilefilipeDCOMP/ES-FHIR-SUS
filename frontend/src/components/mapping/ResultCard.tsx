import type React from 'react';
import type { ResultadoOperacao } from '../../types/ui';

export const ResultCard: React.FC<{ resultado: ResultadoOperacao | null }> = ({ resultado }) => {
  if (!resultado) return null;

  return (
    <section
      role="status"
      aria-live="polite"
      className={`rounded-lg border p-4 space-y-3 ${
        resultado.sucesso ? 'bg-emerald-50 border-emerald-300' : 'bg-red-50 border-red-300'
      }`}
    >
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">
          [{resultado.acao}] {resultado.mensagemResumo}
        </h3>
        <span className="text-[11px] text-slate-500">
          {new Date(resultado.timestamp).toLocaleTimeString('pt-BR')}
        </span>
      </div>

      {resultado.erros.length > 0 && (
        <ul className="list-disc pl-5 space-y-1 text-xs text-red-900">
          {resultado.erros.map((err, idx) => (
            <li key={idx}>
              <strong>{err.local}:</strong> {err.motivo}
            </li>
          ))}
        </ul>
      )}

      {resultado.avisos.length > 0 && (
        <ul className="list-disc pl-5 space-y-1 text-xs text-amber-900">
          {resultado.avisos.map((av, idx) => (
            <li key={idx}>
              <strong>{av.local}:</strong> {av.motivo}
            </li>
          ))}
        </ul>
      )}

      {resultado.payloadSaida && (
        <details className="text-xs">
          <summary className="cursor-pointer font-semibold">Ver saída convertida do teste</summary>
          <pre className="mt-2 p-2.5 rounded bg-slate-900 text-slate-100 overflow-x-auto">
            {JSON.stringify(resultado.payloadSaida, null, 2)}
          </pre>
        </details>
      )}
    </section>
  );
};
