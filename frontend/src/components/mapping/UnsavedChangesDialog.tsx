import type React from 'react';
import type { RecursoFhir } from '../../types/domain';

interface Props {
  recursoDestino: RecursoFhir | null;
  onConfirmarDescarte: () => void;
  onCancelar: () => void;
}

export const UnsavedChangesDialog: React.FC<Props> = ({
  recursoDestino,
  onConfirmarDescarte,
  onCancelar,
}) => {
  if (!recursoDestino) return null;

  return (
    <div className="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
      <div
        role="alertdialog"
        aria-modal="true"
        className="bg-white rounded-xl max-w-md w-full p-5 space-y-4 shadow-xl"
      >
        <h2 className="text-base font-semibold text-slate-900">
          Alterações não salvas no rascunho
        </h2>
        <p className="text-xs text-slate-600">
          Você possui alterações não salvas. Ao trocar para <strong>{recursoDestino}</strong>, o
          rascunho atual será descartado e a última configuração salva continuará ativa.
        </p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onCancelar} className="px-3 py-1.5 text-xs border rounded">
            Continuar editando
          </button>
          <button
            type="button"
            onClick={onConfirmarDescarte}
            className="px-3 py-1.5 text-xs rounded bg-red-600 text-white"
          >
            Descartar e trocar recurso
          </button>
        </div>
      </div>
    </div>
  );
};
