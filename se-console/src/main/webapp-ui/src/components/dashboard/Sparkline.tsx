import { Line, LineChart, ResponsiveContainer, YAxis } from 'recharts';

/**
 * Minimal sparkline. Sized by its container (ResponsiveContainer).
 * Color defaults to the primary token (Jalin red); override for status hues.
 */
export function Sparkline({
  data,
  color = 'hsl(var(--primary))',
  yDomain,
  strokeWidth = 1.5,
}: {
  data: { t: number; v: number }[];
  color?: string;
  yDomain?: [number, number];
  strokeWidth?: number;
}) {
  if (data.length < 2) {
    return <div className="h-full w-full" />;
  }

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
        {yDomain && <YAxis hide domain={yDomain} />}
        <Line
          type="monotone"
          dataKey="v"
          stroke={color}
          strokeWidth={strokeWidth}
          dot={false}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
