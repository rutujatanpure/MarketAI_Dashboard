import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import Navbar from '../components/Navbar'
import CurrencySelector from '../components/CurrencySelector'
import { useAuth } from '../context/AuthContext'
import { apiService } from '../services/apiService'
import { RiUser3Line, RiMailLine, RiSmartphoneLine, RiShieldLine, RiCalendarLine, RiBellLine } from 'react-icons/ri'
import toast from 'react-hot-toast'
import dayjs from 'dayjs'

export default function Profile() {
  const { user } = useAuth()
  const [profile,   setProfile]   = useState(null)
  const [notif,     setNotif]     = useState(true)
  const [updating,  setUpdating]  = useState(false)

  useEffect(() => {
    apiService.get('/api/user/profile').then(r => {
      setProfile(r.data)
      setNotif(r.data.emailNotifications)
    }).catch(() => {})
  }, [])

  const toggleNotif = async () => {
    setUpdating(true)
    try {
      await apiService.put('/api/user/notifications', { enabled: !notif })
      setNotif(v => !v)
      toast.success('Notification preference updated')
    } catch { toast.error('Failed to update') }
    finally { setUpdating(false) }
  }

  const InfoRow = ({ icon, label, value, badge }) => (
    <div className="flex items-center justify-between py-4 border-b border-slate-700/30 last:border-0">
      <div className="flex items-center gap-3 text-slate-400">
        {icon}
        <span className="text-sm font-mono">{label}</span>
      </div>
      {badge
        ? <span className={`text-xs px-2.5 py-1 rounded-full font-mono font-medium ${badge}`}>{value}</span>
        : <span className="text-sm text-slate-200 font-mono">{value}</span>}
    </div>
  )

  return (
    <div className="min-h-screen pt-16">
      <Navbar />
      <div className="max-w-3xl mx-auto px-4 py-10">
        <motion.div initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }}>
          <h1 className="font-display font-bold text-2xl text-slate-100 mb-6">Profile</h1>

          <div className="grid gap-6">
            {/* User info card */}
            <div className="glass-card p-6">
              {/* Avatar */}
              <div className="flex items-center gap-5 mb-6 pb-6 border-b border-slate-700/30">
                <div className="w-16 h-16 rounded-2xl bg-cyan-500/20 border border-cyan-500/30
                                flex items-center justify-center text-2xl font-display font-bold text-cyan-400">
                  {user?.username?.[0]?.toUpperCase()}
                </div>
                <div>
                  <p className="font-display font-bold text-xl text-slate-100">{user?.username}</p>
                  <span className={`text-xs px-2.5 py-1 rounded-full font-mono font-medium mt-1 inline-block ${
                    user?.role === 'ADMIN'
                      ? 'bg-purple-500/15 text-purple-400 border border-purple-500/20'
                      : 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/20'
                  }`}>
                    {user?.role}
                  </span>
                </div>
              </div>

              <InfoRow icon={<RiUser3Line />}     label="Username"  value={profile?.username || user?.username} />
              <InfoRow icon={<RiMailLine />}       label="Email"     value={profile?.email    || user?.email} />
              <InfoRow icon={<RiSmartphoneLine />} label="Mobile"    value={profile?.mobileNumber || '—'} />
              <InfoRow icon={<RiShieldLine />}     label="Role"      value={user?.role}
                badge={user?.role === 'ADMIN'
                  ? 'bg-purple-500/15 text-purple-400 border border-purple-500/20'
                  : 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/20'} />
              <InfoRow icon={<RiCalendarLine />}   label="Member since"
                value={profile?.createdAt ? dayjs(profile.createdAt).format('MMM D, YYYY') : '—'} />
            </div>

            {/* Notifications */}
            <div className="glass-card p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <RiBellLine className="text-slate-400" />
                  <div>
                    <p className="text-sm font-medium text-slate-100">Email Notifications</p>
                    <p className="text-xs text-slate-500 font-mono">Get alerts for price anomalies in your watchlist</p>
                  </div>
                </div>
                <button onClick={toggleNotif} disabled={updating}
                  className={`relative w-12 h-6 rounded-full transition-colors duration-200 ${notif ? 'bg-cyan-500' : 'bg-slate-700'}`}>
                  <div className={`absolute top-1 w-4 h-4 rounded-full bg-white shadow transition-transform duration-200 ${notif ? 'left-7' : 'left-1'}`} />
                </button>
              </div>
            </div>

            {/* Currency */}
            <CurrencySelector />
          </div>
        </motion.div>
      </div>
    </div>
  )
}