import type { ReactNode } from 'react';

/**
 * Thin wrapper around a surface with Tailwind card styling. Kept as a
 * tiny utility rather than pulling in shadcn's Card immediately — easy to
 * swap later by replacing just this file's body.
 */
export function Card({
  children,
  className = '',
  ...rest
}: {
  children: ReactNode;
  className?: string;
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`bg-card border border-border rounded-md ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}
