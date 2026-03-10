import { useState, useEffect, useCallback } from 'react'
import { useWebSocket } from './useWebSocket'
import { aiService } from '../services/aiService'

/**
 * Hook to manage AI analysis for a symbol.
 *
 * Usage:
 *   const { analysis, loading, refresh } = useAiAnalysis('BTCUSDT')
 *
 * Returns:
 *   analysis — AiAnalysisResult object (sentiment, signal, summary, etc.)
 *   loading  — boolean
 *   refresh  — function to trigger fresh analysis
 *   error    — string or null
 */
export function useAiAnalysis(symbol) {
  const [analysis, setAnalysis] = useState(null)
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState(null)

  // Listen for WebSocket pushes of AI results for this symbol
  const wsResult = useWebSocket(
    symbol ? `/topic/analysis/${symbol}` : null,
    { enabled: !!symbol }
  )

  useEffect(() => {
    if (wsResult) setAnalysis(wsResult)
  }, [wsResult])

  // Load cached result on mount
  useEffect(() => {
    if (!symbol) return
    setError(null)
    aiService.getLatest(symbol)
      .then(data => { if (data) setAnalysis(data) })
      .catch(() => {})  // Silently ignore — no cached result is normal
  }, [symbol])

  // Manual trigger for fresh analysis
  const refresh = useCallback(async () => {
    if (!symbol || loading) return
    setLoading(true)
    setError(null)
    try {
      const result = await aiService.analyze(symbol)
      setAnalysis(result)
    } catch (err) {
      setError(err?.response?.data?.message || 'AI analysis failed')
    } finally {
      setLoading(false)
    }
  }, [symbol, loading])

  return { analysis, loading, refresh, error }
}