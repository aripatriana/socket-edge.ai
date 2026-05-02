import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './index.css'

/**
 * TanStack Query client. Tuned for metric-polling style dashboards:
 *   - staleTime 1s so refetches within the same second hit cache (cheap re-renders)
 *   - retry 1 on failure, then surface error to the UI (don't hammer during outage)
 *   - refetchOnWindowFocus false to avoid surprising refetches during typing etc.
 *
 * Per-query overrides (e.g. refetchInterval: 2000 for live metrics) stay with
 * the hook that defines them.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)
