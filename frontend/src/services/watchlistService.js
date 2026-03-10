import { apiService } from './apiService'

/**
 * Frontend watchlist service.
 * Maps to WatchlistController endpoints.
 */
export const watchlistService = {

  /** GET full watchlist for current user */
  async getWatchlist() {
    const { data } = await apiService.get('/api/watchlist')
    return data
  },

  /** Add a crypto symbol */
  async addCrypto(symbol, displayName = '') {
    const { data } = await apiService.post(
      `/api/watchlist/crypto?symbol=${symbol.toUpperCase()}&name=${displayName || symbol}`
    )
    return data
  },

  /** Remove a crypto symbol */
  async removeCrypto(symbol) {
    const { data } = await apiService.delete(
      `/api/watchlist/crypto?symbol=${symbol.toUpperCase()}`
    )
    return data
  },

  /** Add a stock symbol */
  async addStock(symbol, displayName = '') {
    const { data } = await apiService.post(
      `/api/watchlist/stock?symbol=${symbol.toUpperCase()}&name=${displayName || symbol}`
    )
    return data
  },

  /** Remove a stock symbol */
  async removeStock(symbol) {
    const { data } = await apiService.delete(
      `/api/watchlist/stock?symbol=${symbol.toUpperCase()}`
    )
    return data
  },

  /** Clear entire watchlist */
  async clearAll() {
    const { data } = await apiService.delete('/api/watchlist/clear')
    return data
  },

  /** Get all symbol strings from watchlist object */
  getAllSymbols(watchlist) {
    if (!watchlist) return []
    const crypto = (watchlist.cryptoSymbols || []).map(e => e.symbol || e)
    const stocks = (watchlist.stockSymbols  || []).map(e => e.symbol || e)
    return [...crypto, ...stocks]
  }
}