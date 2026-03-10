import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import ProtectedRoute  from './components/ProtectedRoute'
import AdminRoute      from './components/AdminRoute'
import Home            from './pages/Home'
import Login           from './pages/Login'
import Register        from './pages/Register'
import Dashboard       from './pages/Dashboard'
import Profile         from './pages/Profile'
import WatchlistPage   from './pages/Watchlist'
import AdminDashboard  from './pages/AdminDashboard'
import About           from './pages/About'
import NotFound        from './pages/NotFound'

// Registers React Router navigate() globally so apiService interceptor
// can redirect on 401 without using window.location.href (avoids chrome-error://)
function NavigateRegistrar() {
  const navigate = useNavigate()
  useEffect(() => {
    window.__reactRouterNavigate = navigate
    return () => { delete window.__reactRouterNavigate }
  }, [navigate])
  return null
}

export default function App() {
  return (
    <div className='noise min-h-screen bg-navy-950 bg-grid-pattern'>
      {/* Ambient glow blobs */}
      <div className='fixed inset-0 overflow-hidden pointer-events-none'>
        <div className='absolute -top-40 -left-40 w-96 h-96 bg-cyan-500/5 rounded-full blur-3xl' />
        <div className='absolute top-1/3 -right-40 w-96 h-96 bg-emerald-500/5 rounded-full blur-3xl' />
        <div className='absolute bottom-0 left-1/3 w-96 h-96 bg-cyan-500/3 rounded-full blur-3xl' />
      </div>

      <NavigateRegistrar />

      <Routes>
        {/* Public */}
        <Route path='/'         element={<Home />} />
        <Route path='/about'    element={<About />} />
        <Route path='/login'    element={<Login />} />
        <Route path='/register' element={<Register />} />

        {/* Protected — requires login */}
        <Route element={<ProtectedRoute />}>
          <Route path='/dashboard' element={<Dashboard />} />
          <Route path='/profile'   element={<Profile />} />
          <Route path='/watchlist' element={<WatchlistPage />} />
        </Route>

        {/* Admin only */}
        <Route element={<AdminRoute />}>
          <Route path='/admin' element={<AdminDashboard />} />
        </Route>

        {/* Fallback */}
        <Route path='/404' element={<NotFound />} />
        <Route path='*'    element={<Navigate to='/404' replace />} />
      </Routes>
    </div>
  )
}