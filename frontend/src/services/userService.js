import { apiService } from './apiService'

/**
 * User service — wraps UserController endpoints.
 */
export const userService = {

  async getProfile() {
    const { data } = await apiService.get('/api/user/profile')
    return data
  },

  async getWatchlist() {
    const { data } = await apiService.get('/api/user/watchlist')
    return data
  },

  async addToWatchlist(symbol) {
    const { data } = await apiService.post(`/api/user/watchlist?symbol=${symbol}`)
    return data
  },

  async removeFromWatchlist(symbol) {
    const { data } = await apiService.delete(`/api/user/watchlist?symbol=${symbol}`)
    return data
  },

  async updateCurrency(currency) {
    const { data } = await apiService.put('/api/user/currency', { currency })
    return data
  },

  async updateNotifications(enabled) {
    const { data } = await apiService.put('/api/user/notifications', { enabled })
    return data
  }
}