import type React from 'react';
import type { ResumoObrigatorios } from '../../utils/mappingValidation';

interface Props {
  resumo: ResumoObrigatorios;
  onVerPendencias: () => void;
}

export const ProgressBanner: React.FC<Props> = ({ resumo, onVerPendencias }) => {
  const percentual =
    resumo.total === 0 ? 100 : Math.round((resumo.mapeados / resumo.total) * 100);

  return (
    <div className="bg-white border rounded-lg p-4 flex flex-wrap items-center justify-between gap-4">
      <div className="flex-1 min-w-[260px] space-y-1.5">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-slate-800">
            {resumo.mapeados} de {resumo.total} campos obrigatórios mapeados
          </span>
          <span className="text-slate-500">{percentual}%</span>
        </div>
        <div className="h-2 w-full bg-slate-200 rounded-full overflow-hidden">
          <div
            className={`h-full transition-all ${
              resumo.pendentes.length === 0 ? 'bg-emerald-500' : 'bg-amber-500'
            }`}
            style={{ width: `${percentual}%` }}
          />
        </div>
      </div>

      <div className="flex items-center gap-4 text-xs">
        <span className="text-red-700 font-medium">{resumo.errosUx.length} erro(s)</span>
        <span className="text-amber-700 font-medium">{resumo.avisosUx.length} aviso(s)</span>
        {resumo.errosUx.length > 0 && (
          <button
            type="button"
            onClick={onVerPendencias}
            className="text-blue-600 font-semibold hover:underline"
          >
            Ver pendências
          </button>
        )}
      </div>
    </div>
  );
};
