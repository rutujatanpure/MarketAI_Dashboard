import { apiService } from './apiService'

export const authService = {
  login:    (email, password)  => apiService.post('/api/auth/login',    { email, password }).then(r => r.data),
  register: (payload)          => apiService.post('/api/auth/register', payload).then(r => r.data),
}