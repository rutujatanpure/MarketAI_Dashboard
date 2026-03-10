import { useState, useEffect } from 'react'
import { apiService } from '../services/apiService'

/**
 * Fetch price history for a symbol.
 * @param {string} symbol  e.g. "BTCUSDT" or "AAPL"
 * @param {string} type    "crypto" | "stock"
 * @param {number} hours   how many hours of history (default 24)
 */
export function usePriceHistory(symbol, type = 'crypto', hours = 24) {
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState(null)

  useEffect(() => {
    if (!symbol) return
    setLoading(true)
    setError(null)

    const endpoint = type === 'crypto'
      ? `/api/crypto/history?symbol=${symbol}&hours=${hours}`
      : `/api/stocks/history?symbol=${symbol}&hours=${hours}`

    apiService.get(endpoint)
      .then(r => setHistory(r.data || []))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [symbol, type, hours])

  return { history, loading, error }
}