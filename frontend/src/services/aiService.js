import { apiService } from './apiService'

export const aiService = {
  analyze:   (symbol) => apiService.get(`/api/ai/analyze?symbol=${symbol}`).then(r => r.data),
  getLatest: (symbol) => apiService.get(`/api/ai/latest?symbol=${symbol}`).then(r => r.data),
}