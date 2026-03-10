import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from './LoadingSpinner'

export default function ProtectedRoute() {
  const { isAuth } = useAuth()
  if (isAuth === undefined) return <LoadingSpinner fullScreen />
  return isAuth ? <Outlet /> : <Navigate to="/login" replace />
}