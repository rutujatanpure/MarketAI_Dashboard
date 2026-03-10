import { apiService } from './apiService'

/**
 * Admin service — wraps AdminController endpoints.
 * All calls require ADMIN role (enforced by backend Spring Security).
 */
export const adminService = {

  async getAllUsers() {
    const { data } = await apiService.get('/api/admin/users')
    return data
  },

  async getStats() {
    const { data } = await apiService.get('/api/admin/stats')
    return data
  },

  async toggleUserStatus(userId) {
    const { data } = await apiService.put(`/api/admin/users/${userId}/toggle`)
    return data
  },

  async deleteUser(userId) {
    const { data } = await apiService.delete(`/api/admin/users/${userId}`)
    return data
  }
}