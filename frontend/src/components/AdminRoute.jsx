import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from './Loadingspinner'  // Uppercase "S"

export default function AdminRoute() {
  const { isAuth, isAdmin } = useAuth()
  if (isAuth === undefined) return <LoadingSpinner fullScreen />
  if (!isAuth)  return <Navigate to="/login" replace />
  if (!isAdmin) return <Navigate to="/dashboard" replace />
  return <Outlet />
}