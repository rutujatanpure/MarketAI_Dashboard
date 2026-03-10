import { useState, useRef, useEffect } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { usePrices } from '../context/PriceContext'
import {
  RiLineChartLine, RiMenuLine, RiCloseLine,
  RiDashboardLine, RiUser3Line, RiBookmarkLine,
  RiShieldLine, RiLogoutBoxLine, RiLoginBoxLine,
  RiUserAddLine, RiArrowDownSLine,
  RiHome4Line,
} from 'react-icons/ri'

function useStyles() {
  useEffect(() => {
    if (document.getElementById('nb-css')) return
    const s = document.createElement('style')
    s.id = 'nb-css'
    s.textContent = `
      @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700;800;900&display=swap');
      .nb-link {
        display:flex;align-items:center;gap:6px;padding:8px 13px;
        border-radius:10px;font-size:13.5px;font-weight:600;
        color:#94a3b8;text-decoration:none;white-space:nowrap;
        transition:color .18s,background .18s;cursor:pointer;
      }
      .nb-link:hover{color:#fff;background:rgba(255,255,255,.06);}
      .nb-link.active{color:#34d399;background:rgba(16,185,129,.1);border:1px solid rgba(16,185,129,.2);}
      .nb-drop{
        display:flex;align-items:center;gap:10px;padding:10px 16px;
        font-size:13.5px;color:#cbd5e1;text-decoration:none;
        transition:background .14s;cursor:pointer;
        background:none;border:none;width:100%;text-align:left;font-family:'DM Sans',sans-serif;
      }
      .nb-drop:hover{background:rgba(255,255,255,.04);}
      .nb-mob{
        display:flex;align-items:center;gap:8px;padding:12px 14px;
        border-radius:12px;font-size:14px;font-weight:600;color:#cbd5e1;
        text-decoration:none;transition:background .14s;width:100%;text-align:left;
        background:none;border:none;font-family:'DM Sans',sans-serif;
      }
      .nb-mob:hover{background:rgba(255,255,255,.05);color:#fff;}
    `
    document.head.appendChild(s)
  }, [])
}

const C = {
  bg:'#070d07', card:'#0d1a0d',
  em:'#10b981', amber:'#f59e0b',
  border:'rgba(16,185,129,.18)',
  grad:'linear-gradient(90deg,#10b981,#14b8a6)',
}

const MKTG = [
  { label:'Features', href:'#features' },
  { label:'Markets',  href:'#trending' },
  { label:'About',    href:'#stats' },
]
const NAVS = [
  { to:'/dashboard', label:'Dashboard', icon:RiDashboardLine },
  { to:'/watchlist', label:'Watchlist',  icon:RiBookmarkLine },
]

export default function Navbar() {
  useStyles()
  const { user, isAdmin, logout } = useAuth()
  const { connected } = usePrices?.() || {}
  const navigate = useNavigate()
  const [mob, setMob]   = useState(false)
  const [drop, setDrop] = useState(false)
  const [sc, setSc]     = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    const f = () => setSc(window.scrollY > 8)
    window.addEventListener('scroll', f, { passive:true })
    return () => window.removeEventListener('scroll', f)
  }, [])

  useEffect(() => {
    const f = e => { if (ref.current && !ref.current.contains(e.target)) setDrop(false) }
    document.addEventListener('mousedown', f)
    return () => document.removeEventListener('mousedown', f)
  }, [])

  useEffect(() => {
    const f = () => { if (window.innerWidth >= 768) setMob(false) }
    window.addEventListener('resize', f)
    return () => window.removeEventListener('resize', f)
  }, [])

  const hdr = {
    position:'fixed',top:0,left:0,right:0,zIndex:1000,
    fontFamily:"'DM Sans',sans-serif",
    transition:'all .28s ease',
    background: sc ? 'rgba(7,13,7,.97)' : 'transparent',
    backdropFilter: sc ? 'blur(20px)' : 'none',
    borderBottom: `1px solid ${sc ? C.border : 'transparent'}`,
    boxShadow: sc ? '0 4px 24px rgba(0,0,0,.35)' : 'none',
  }

  return (
    <header style={hdr}>
      <div style={{
        maxWidth:1280, margin:'0 auto',
        padding:'0 20px', height:64,
        display:'flex', alignItems:'center',
        justifyContent:'space-between', gap:12,
      }}>

        {/* Logo */}
        <Link to="/" style={{ display:'flex', alignItems:'center', gap:8, flexShrink:0, textDecoration:'none' }}>
          <div style={{
            width:34, height:34, borderRadius:10, flexShrink:0,
            background:'linear-gradient(135deg,#34d399,#0d9488)',
            display:'flex', alignItems:'center', justifyContent:'center',
            boxShadow:'0 3px 10px rgba(16,185,129,.28)',
          }}>
            <RiLineChartLine style={{ color:'#fff', fontSize:17 }}/>
          </div>
          <span style={{ fontWeight:800, fontSize:17, color:'#fff', letterSpacing:'-.02em', whiteSpace:'nowrap' }}>
            Market<span style={{ color:C.em }}>AI</span>
          </span>
        </Link>

        {/* Center nav */}
        <nav id="nb-desk" style={{ display:'flex', alignItems:'center', gap:2, flex:1, justifyContent:'center' }}>
          {user ? (
            <>
              {/* ── Home link added ── */}
              <NavLink to="/" end className={({ isActive }) => `nb-link${isActive?' active':''}`}>
                <RiHome4Line style={{ fontSize:15 }}/>Home
              </NavLink>

              {NAVS.map(n => (
                <NavLink key={n.to} to={n.to} className={({ isActive }) => `nb-link${isActive?' active':''}`}>
                  <n.icon style={{ fontSize:15 }}/>{n.label}
                </NavLink>
              ))}
              {isAdmin && (
                <NavLink to="/admin" className={({ isActive }) => `nb-link${isActive?' active':''}`} style={{ color:C.amber }}>
                  <RiShieldLine style={{ fontSize:15 }}/>Admin
                </NavLink>
              )}
            </>
          ) : (
            <>
              {/* ── Home link on landing page too ── */}
              <NavLink to="/" end className={({ isActive }) => `nb-link${isActive?' active':''}`}>
                <RiHome4Line style={{ fontSize:15 }}/>Home
              </NavLink>
              {MKTG.map(l => (
                <a key={l.label} href={l.href} className="nb-link">{l.label}</a>
              ))}
            </>
          )}
        </nav>

        {/* Right controls */}
        <div style={{ display:'flex', alignItems:'center', gap:8, flexShrink:0 }}>

          {user ? (
            <div style={{ position:'relative' }} ref={ref}>
              <button onClick={() => setDrop(v => !v)}
                style={{
                  display:'flex', alignItems:'center', gap:7,
                  padding:'5px 10px 5px 5px', borderRadius:14,
                  background:C.card, border:`1px solid ${C.border}`,
                  cursor:'pointer', fontFamily:'inherit', transition:'border-color .2s',
                }}>
                <div style={{
                  width:30, height:30, borderRadius:9, flexShrink:0,
                  background:'linear-gradient(135deg,#34d399,#0d9488)',
                  display:'flex', alignItems:'center', justifyContent:'center',
                  color:'#fff', fontSize:13, fontWeight:800,
                }}>
                  {user.username?.[0]?.toUpperCase() || 'U'}
                </div>
                <span id="nb-uname" style={{ fontSize:13, fontWeight:700, color:'#fff', whiteSpace:'nowrap' }}>
                  {user.username}
                </span>
                <RiArrowDownSLine style={{ color:'#64748b', fontSize:14, transition:'transform .2s', transform: drop ? 'rotate(180deg)' : 'none' }}/>
              </button>

              <AnimatePresence>
                {drop && (
                  <motion.div
                    initial={{ opacity:0, y:8, scale:.96 }}
                    animate={{ opacity:1, y:0, scale:1 }}
                    exit={{ opacity:0, y:8, scale:.96 }}
                    transition={{ duration:.14 }}
                    style={{
                      position:'absolute', right:0, top:48, width:220,
                      background:C.card, border:`1px solid ${C.border}`,
                      borderRadius:16, padding:'8px 0',
                      boxShadow:'0 16px 48px rgba(0,0,0,.7)', zIndex:999,
                    }}>
                    <div style={{ padding:'10px 16px', borderBottom:`1px solid ${C.border}`, marginBottom:4 }}>
                      <p style={{ fontSize:13, fontWeight:700, color:'#fff', margin:0 }}>{user.username}</p>
                      <p style={{ fontSize:11, color:'#64748b', margin:0, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{user.email}</p>
                    </div>
                    {[
                      { to:'/',          label:'Home',       icon:RiHome4Line     }, // ← added
                      { to:'/dashboard', label:'Dashboard',  icon:RiDashboardLine },
                      { to:'/profile',   label:'My Profile', icon:RiUser3Line     },
                      { to:'/watchlist', label:'Watchlist',  icon:RiBookmarkLine  },
                      ...(isAdmin ? [{ to:'/admin', label:'Admin Panel', icon:RiShieldLine }] : []),
                    ].map(item => (
                      <Link key={item.to} to={item.to} onClick={() => setDrop(false)} className="nb-drop">
                        <item.icon style={{ color:'#64748b', fontSize:15 }}/>{item.label}
                      </Link>
                    ))}
                    <div style={{ borderTop:`1px solid ${C.border}`, marginTop:4, paddingTop:4 }}>
                      <button onClick={() => { logout(); navigate('/'); setDrop(false) }}
                        className="nb-drop" style={{ color:'#f87171' }}>
                        <RiLogoutBoxLine style={{ fontSize:15 }}/>Logout
                      </button>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

          ) : (
            <>
              <Link to="/login" id="nb-signin"
                style={{ padding:'7px 16px', fontSize:13.5, fontWeight:600, color:'#cbd5e1', borderRadius:10, transition:'color .2s', textDecoration:'none', whiteSpace:'nowrap' }}
                onMouseEnter={e => e.currentTarget.style.color='#fff'}
                onMouseLeave={e => e.currentTarget.style.color='#cbd5e1'}>
                Sign in
              </Link>
              <Link to="/register"
                style={{ padding:'8px 18px', fontSize:13.5, fontWeight:700, background:C.grad, color:'#fff', borderRadius:11, boxShadow:'0 3px 12px rgba(16,185,129,.28)', transition:'opacity .2s', textDecoration:'none', whiteSpace:'nowrap' }}
                onMouseEnter={e => e.currentTarget.style.opacity='.88'}
                onMouseLeave={e => e.currentTarget.style.opacity='1'}>
                Get Started
              </Link>
            </>
          )}

          {/* Hamburger */}
          <button id="nb-ham" onClick={() => setMob(v => !v)}
            style={{ padding:7, color:'#94a3b8', borderRadius:8, background:'none', border:'none', cursor:'pointer', display:'flex', alignItems:'center' }}>
            {mob ? <RiCloseLine size={21}/> : <RiMenuLine size={21}/>}
          </button>
        </div>
      </div>

      {/* Mobile drawer */}
      <AnimatePresence>
        {mob && (
          <motion.div
            initial={{ height:0, opacity:0 }}
            animate={{ height:'auto', opacity:1 }}
            exit={{ height:0, opacity:0 }}
            transition={{ duration:.2 }}
            style={{ overflow:'hidden', background:'rgba(7,13,7,.98)', borderTop:`1px solid ${C.border}` }}>
            <div style={{ padding:'12px 16px 16px' }}>
              {user ? (
                <>
                  <div style={{ display:'flex', alignItems:'center', gap:10, padding:'10px 14px', marginBottom:8, background:C.card, borderRadius:12, border:`1px solid ${C.border}` }}>
                    <div style={{ width:34, height:34, borderRadius:10, flexShrink:0, background:'linear-gradient(135deg,#34d399,#0d9488)', display:'flex', alignItems:'center', justifyContent:'center', color:'#fff', fontSize:14, fontWeight:800 }}>
                      {user.username?.[0]?.toUpperCase()}
                    </div>
                    <div style={{ minWidth:0 }}>
                      <p style={{ fontSize:13, fontWeight:700, color:'#fff', margin:0 }}>{user.username}</p>
                      <p style={{ fontSize:11, color:'#64748b', margin:0, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{user.email}</p>
                    </div>
                    {connected && (
                      <div style={{ marginLeft:'auto', display:'flex', alignItems:'center', gap:4 }}>
                        <span style={{ width:6, height:6, borderRadius:'50%', background:C.em, display:'inline-block' }}/>
                        <span style={{ fontSize:9, fontFamily:'monospace', color:C.em }}>LIVE</span>
                      </div>
                    )}
                  </div>

                  {/* ── Home first in mobile menu ── */}
                  <NavLink to="/" end onClick={() => setMob(false)} className="nb-mob">
                    <RiHome4Line style={{ fontSize:16 }}/>Home
                  </NavLink>

                  {NAVS.map(n => (
                    <NavLink key={n.to} to={n.to} onClick={() => setMob(false)} className="nb-mob">
                      <n.icon style={{ fontSize:16 }}/>{n.label}
                    </NavLink>
                  ))}
                  {isAdmin && (
                    <NavLink to="/admin" onClick={() => setMob(false)} className="nb-mob" style={{ color:C.amber }}>
                      <RiShieldLine style={{ fontSize:16 }}/>Admin Panel
                    </NavLink>
                  )}
                  <button onClick={() => { logout(); navigate('/'); setMob(false) }}
                    className="nb-mob" style={{ color:'#f87171', cursor:'pointer' }}>
                    <RiLogoutBoxLine style={{ fontSize:16 }}/>Logout
                  </button>
                </>
              ) : (
                <>
                  {/* ── Home first for logged-out mobile ── */}
                  <NavLink to="/" end onClick={() => setMob(false)} className="nb-mob">
                    <RiHome4Line style={{ fontSize:16 }}/>Home
                  </NavLink>
                  {MKTG.map(l => (
                    <a key={l.label} href={l.href} onClick={() => setMob(false)} className="nb-mob" style={{ display:'flex' }}>
                      {l.label}
                    </a>
                  ))}
                  <div style={{ paddingTop:8, display:'flex', flexDirection:'column', gap:6 }}>
                    <Link to="/login" onClick={() => setMob(false)} className="nb-mob" style={{ display:'flex', justifyContent:'center' }}>
                      <RiLoginBoxLine style={{ fontSize:16 }}/>Sign in
                    </Link>
                    <Link to="/register" onClick={() => setMob(false)}
                      style={{ display:'flex', alignItems:'center', justifyContent:'center', gap:8, padding:'13px 14px', borderRadius:12, color:'#fff', fontSize:14, fontWeight:700, background:C.grad, boxShadow:'0 3px 12px rgba(16,185,129,.25)', textDecoration:'none' }}>
                      <RiUserAddLine style={{ fontSize:16 }}/>Get Started Free
                    </Link>
                  </div>
                </>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <style>{`
        @media (max-width: 767px) {
          #nb-desk   { display: none !important; }
          #nb-signin { display: none !important; }
          #nb-uname  { display: none !important; }
        }
        @media (min-width: 768px) {
          #nb-ham { display: none !important; }
        }
      `}</style>
    </header>
  )
}