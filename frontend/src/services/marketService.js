import { apiService } from './apiService'

/**
 * Market data service — wraps all price and history endpoints.
 */
export const marketService = {

  // ── Crypto ──────────────────────────────────────────────────────────────────

  async getLatestCrypto(symbol) {
    const { data } = await apiService.get(`/api/crypto/latest?symbol=${symbol}`)
    return data
  },

  async getAllCrypto() {
    const { data } = await apiService.get('/api/crypto/all')
    return data
  },

  async getCryptoHistory(symbol, hours = 24) {
    const { data } = await apiService.get(`/api/crypto/history?symbol=${symbol}&hours=${hours}`)
    return data
  },

  async getCryptoCandles(symbol, interval = '1h', limit = 100) {
    const { data } = await apiService.get(
      `/api/crypto/candles?symbol=${symbol}&interval=${interval}&limit=${limit}`
    )
    return data
  },

  async getCryptoSymbols() {
    const { data } = await apiService.get('/api/crypto/symbols')
    return data
  },

  // ── Stocks ──────────────────────────────────────────────────────────────────

  async getLatestStock(symbol) {
    const { data } = await apiService.get(`/api/stocks/latest?symbol=${symbol}`)
    return data
  },

  async getAllStocks() {
    const { data } = await apiService.get('/api/stocks/all')
    return data
  },

  async getStockHistory(symbol, hours = 48) {
    const { data } = await apiService.get(`/api/stocks/history?symbol=${symbol}&hours=${hours}`)
    return data
  },

  async getStockCandles(symbol, interval = '1d', limit = 30) {
    const { data } = await apiService.get(
      `/api/stocks/candles?symbol=${symbol}&interval=${interval}&limit=${limit}`
    )
    return data
  },

  async getStockSymbols() {
    const { data } = await apiService.get('/api/stocks/symbols')
    return data
  },

  // ── Historical (OHLCV) ───────────────────────────────────────────────────────

  async getCandles(symbol, interval = '1h', limit = 100) {
    const { data } = await apiService.get(
      `/api/history/candles?symbol=${symbol}&interval=${interval}&limit=${limit}`
    )
    return data
  },

  // ── Alerts ───────────────────────────────────────────────────────────────────

  async getRecentAlerts() {
    const { data } = await apiService.get('/api/alerts/recent')
    return data
  },

  async getAlertsBySymbol(symbol) {
    const { data } = await apiService.get(`/api/alerts?symbol=${symbol}`)
    return data
  },

  // ── Currencies ───────────────────────────────────────────────────────────────

  async getCurrencyRates() {
    const { data } = await apiService.get('/api/market/currencies')
    return data
  }
}