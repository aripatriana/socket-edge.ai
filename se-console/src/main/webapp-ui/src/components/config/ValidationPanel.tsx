import { AlertCircle, AlertTriangle, CheckCircle2 } from 'lucide-react';
import type { ValidateResponse } from '../../api/config.types';

interface Props {
  result: ValidateResponse | null;
  isValidating?: boolean;
}

/**
 * Bottom panel showing the last validation result.
 *
 * <p>States:
 * <ul>
 *   <li>null → hidden</li>
 *   <li>valid:true, no warnings → green "Looks good"</li>
 *   <li>valid:true, with warnings → amber, warnings listed</li>
 *   <li>valid:false → red, errors + warnings listed</li>
 * </ul>
 */
export function ValidationPanel({ result, isValidating }: Props) {
  if (isValidating) {
    return (
      <div className="border-t border-border px-4 py-2 text-[12px] text-muted-foreground font-mono">
        Validating…
      </div>
    );
  }

  if (!result) return null;

  const { valid, errors, warnings, durationMs } = result;
  const hasWarnings = warnings.length > 0;

  if (valid && !hasWarnings) {
    return (
      <div className="border-t border-emerald-200 bg-emerald-50 px-4 py-2 flex items-center gap-2 text-[12px] text-emerald-800">
        <CheckCircle2 className="w-3.5 h-3.5" />
        <span>Validation passed.</span>
        <span className="text-emerald-700/70 font-mono text-[11px]">
          ({durationMs}ms)
        </span>
      </div>
    );
  }

  const tone = valid
    ? 'border-amber-200 bg-amber-50'
    : 'border-red-200 bg-red-50';
  const headerTone = valid ? 'text-amber-800' : 'text-red-800';

  return (
    <div className={`border-t ${tone}`} style={{ maxHeight: 200 }}>
      <header className={`px-4 py-2 flex items-center gap-2 text-[12px] font-semibold ${headerTone}`}>
        {valid ? (
          <AlertTriangle className="w-3.5 h-3.5" />
        ) : (
          <AlertCircle className="w-3.5 h-3.5" />
        )}
        <span>
          {valid
            ? `Validation passed with ${warnings.length} warning${warnings.length === 1 ? '' : 's'}`
            : `Validation failed — ${errors.length} error${errors.length === 1 ? '' : 's'}${
                hasWarnings ? `, ${warnings.length} warning${warnings.length === 1 ? '' : 's'}` : ''
              }`}
        </span>
        <span className="ml-auto font-mono text-[11px] opacity-70">{durationMs}ms</span>
      </header>
      <ul className="px-4 pb-3 space-y-1 overflow-y-auto text-[11px] font-mono" style={{ maxHeight: 160 }}>
        {errors.map((m, i) => (
          <Row key={`e${i}`} message={m} />
        ))}
        {warnings.map((m, i) => (
          <Row key={`w${i}`} message={m} />
        ))}
      </ul>
    </div>
  );
}

function Row({ message }: { message: import('../../api/config.types').ConfigValidationMessage }) {
  const sev = message.severity === 'error' ? 'text-red-700' : 'text-amber-700';
  return (
    <li className={sev}>
      <span className="text-muted-foreground">
        {String(message.line).padStart(3)}:{String(message.column).padStart(2)}
      </span>
      <span className="mx-2">·</span>
      <span>{message.message}</span>
      {message.rule && (
        <span className="text-muted-foreground ml-2">[{message.rule}]</span>
      )}
    </li>
  );
}
