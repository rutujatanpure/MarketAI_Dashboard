/**
 * Login.jsx — zero Tailwind dependencies, fully inline styles
 *
 * ONLY CHANGE vs original: handle() now does role-based redirect:
 *   ADMIN → /admin   |   USER → /dashboard (or intended route)
 * Everything else — UI, styles, layout, animations — 100% identical.
 */
import { useState, useEffect } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import Navbar from '../components/Navbar'

import {
  RiMailLine, RiLockPasswordLine, RiEyeLine, RiEyeOffLine,
  RiArrowRightLine, RiLineChartLine, RiShieldCheckLine,
  RiArrowUpLine, RiArrowDownLine, RiBrainLine, RiPulseLine,
} from 'react-icons/ri'
import toast from 'react-hot-toast'

/* ── inject font + resets once ── */
function useLoginStyles() {
  useEffect(() => {
    if (document.getElementById('login-css')) return
    const s = document.createElement('style')
    s.id = 'login-css'
    s.textContent = `
      @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700;800;900&display=swap');
      body { background:#070d07!important; margin:0; padding:0; font-family:'DM Sans',sans-serif!important; }
      .li-input {
        width:100%; background:#0d1a0d;
        border:1px solid rgba(16,185,129,.18);
        border-radius:12px;
        padding:11px 42px 11px 40px;
        font-size:14px; color:#fff;
        outline:none; transition:border-color .18s;
        font-family:'DM Sans',sans-serif;
        box-sizing:border-box;
      }
      .li-input:focus { border-color:rgba(16,185,129,.5); }
      .li-input::placeholder { color:#475569; }
      .li-stat { display:flex; justify-content:space-between; align-items:center; padding:10px 12px; background:rgba(10,26,10,.8); border:1px solid rgba(16,185,129,.15); border-radius:12px; }
    `
    document.head.appendChild(s)
  }, [])
}

const T = {
  bg:'#070d07', card:'#0d1a0d',
  em:'#10b981', red:'#ef4444',
  s3:'#cbd5e1', s4:'#94a3b8', s5:'#64748b', s6:'#475569', s7:'#334155',
  border:'rgba(16,185,129,.18)',
  grad:'linear-gradient(90deg,#10b981,#14b8a6)',
}

const LIVE = [
  { sym:'BTC',      price:'$67,420', chg:'+2.34%', up:true  },
  { sym:'RELIANCE', price:'₹2,987',  chg:'+1.24%', up:true  },
  { sym:'NVDA',     price:'$924.10', chg:'+3.76%', up:true  },
  { sym:'ETH',      price:'$3,512',  chg:'+1.87%', up:true  },
  { sym:'TCS',      price:'₹4,121',  chg:'+0.87%', up:true  },
  { sym:'SOL',      price:'$178.5',  chg:'-0.92%', up:false },
]

const PERKS = [
  { icon:RiPulseLine,       text:'Real-time NSE, BSE & Crypto feeds' },
  { icon:RiBrainLine,       text:'AI-powered BUY/SELL/HOLD signals' },
  { icon:RiShieldCheckLine, text:'Bank-grade security & encryption' },
]

export default function Login() {
  useLoginStyles()
  const { login }  = useAuth()
  const navigate   = useNavigate()
  const location   = useLocation()

  const [form, setForm]       = useState({ email:'', password:'' })
  const [showPw, setShowPw]   = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')

  // ── FIXED: role-based redirect after successful login ────────────────────
  const handle = async e => {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      const userData = await login(form.email, form.password)  // AuthContext.login() returns user object
      toast.success('Welcome back!')

      if (userData?.role === 'ADMIN') {
        navigate('/admin', { replace: true })                  // Admin → admin dashboard
      } else {
        const intended = location.state?.from?.pathname
        const dest = (intended && intended !== '/login' && intended !== '/') ? intended : '/dashboard'
        navigate(dest, { replace: true })                      // User → dashboard (or intended page)
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid email or password')
    } finally { setLoading(false) }
  }
  // ─────────────────────────────────────────────────────────────────────────

  const iconWrap  = { position:'relative' }
  const iconLeft  = { position:'absolute', left:12, top:'50%', transform:'translateY(-50%)', color:T.s5, fontSize:16, pointerEvents:'none' }
  const iconRight = { position:'absolute', right:12, top:'50%', transform:'translateY(-50%)', color:T.s5, fontSize:16, cursor:'pointer', background:'none', border:'none', display:'flex', alignItems:'center' }

  return (
    <div style={{ minHeight:'100vh', background:T.bg, fontFamily:"'DM Sans',sans-serif", overflowX:'hidden' }}>
      <Navbar/>

      <div style={{ minHeight:'100vh', paddingTop:64, display:'flex' }}>

        {/* ── Left panel ── */}
        <div id="login-left" style={{
          width:'50%', flexShrink:0,
          background:'linear-gradient(135deg,#0a1a0a 0%,#0d2b1a 55%,#071a10 100%)',
          position:'relative', overflow:'hidden',
          display:'flex', flexDirection:'column',
          justifyContent:'space-between',
          padding:'48px 44px',
        }}>
          <div style={{ position:'absolute', inset:0, opacity:.04, backgroundImage:'linear-gradient(#10b981 1px,transparent 1px),linear-gradient(90deg,#10b981 1px,transparent 1px)', backgroundSize:'48px 48px', pointerEvents:'none' }}/>
          <div style={{ position:'absolute', top:'25%', left:'30%', width:240, height:240, background:'rgba(16,185,129,.1)', borderRadius:'50%', filter:'blur(44px)', pointerEvents:'none' }}/>
          <div style={{ position:'absolute', bottom:'20%', right:'20%', width:160, height:160, background:'rgba(20,184,166,.08)', borderRadius:'50%', filter:'blur(36px)', pointerEvents:'none' }}/>

          <motion.div initial={{ opacity:0, x:-16 }} animate={{ opacity:1, x:0 }} transition={{ delay:.1 }}
            style={{ position:'relative', zIndex:2 }}>
            <div style={{ display:'flex', alignItems:'center', gap:7, marginBottom:22 }}>
              <span style={{ width:7, height:7, borderRadius:'50%', background:T.em, display:'inline-block', animation:'mai-pulse 2s ease-in-out infinite' }}/>
              <span style={{ fontSize:10, fontFamily:'monospace', color:T.em, fontWeight:700, textTransform:'uppercase', letterSpacing:'.1em' }}>Markets are live</span>
            </div>
            <h2 style={{ fontWeight:900, fontSize:'clamp(26px,3vw,38px)', color:'#fff', lineHeight:1.15, margin:'0 0 12px', letterSpacing:'-.02em' }}>
              Your edge in<br/>
              <span style={{ background:'linear-gradient(90deg,#34d399,#5eead4)', WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent', backgroundClip:'text' }}>every market</span>
            </h2>
            <p style={{ color:T.s4, fontSize:13.5, lineHeight:1.65, maxWidth:300, margin:0 }}>
              Professional-grade market intelligence with AI signals for NSE, BSE, and 50+ crypto pairs.
            </p>
          </motion.div>

          <motion.div initial={{ opacity:0, y:16 }} animate={{ opacity:1, y:0 }} transition={{ delay:.22 }}
            style={{ position:'relative', zIndex:2 }}>
            <p style={{ fontSize:9, fontFamily:'monospace', color:T.s6, textTransform:'uppercase', letterSpacing:'.08em', marginBottom:10 }}>Live Prices</p>
            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:8 }}>
              {LIVE.map((s,i) => (
                <motion.div key={s.sym} initial={{ opacity:0, y:8 }} animate={{ opacity:1, y:0 }} transition={{ delay:.28+i*.05 }}
                  className="li-stat">
                  <div>
                    <p style={{ fontSize:11, fontWeight:700, color:'#fff', fontFamily:'monospace', margin:'0 0 2px' }}>{s.sym}</p>
                    <p style={{ fontSize:10, color:T.s4, margin:0 }}>{s.price}</p>
                  </div>
                  <span style={{ fontSize:10, fontWeight:700, fontFamily:'monospace', color:s.up?T.em:T.red, display:'flex', alignItems:'center', gap:2 }}>
                    {s.up?<RiArrowUpLine style={{ fontSize:9 }}/>:<RiArrowDownLine style={{ fontSize:9 }}/>}{s.chg}
                  </span>
                </motion.div>
              ))}
            </div>
          </motion.div>

          <motion.div initial={{ opacity:0, y:12 }} animate={{ opacity:1, y:0 }} transition={{ delay:.46 }}
            style={{ position:'relative', zIndex:2 }}>
            {PERKS.map((p,i) => (
              <div key={i} style={{ display:'flex', alignItems:'center', gap:12, marginBottom: i < PERKS.length-1 ? 10 : 0 }}>
                <div style={{ width:32, height:32, borderRadius:9, background:'rgba(16,185,129,.14)', border:'1px solid rgba(16,185,129,.2)', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                  <p.icon style={{ color:T.em, fontSize:14 }}/>
                </div>
                <span style={{ fontSize:13, color:T.s4 }}>{p.text}</span>
              </div>
            ))}
          </motion.div>
        </div>

        {/* ── Right form panel ── */}
        <div style={{ flex:1, display:'flex', alignItems:'center', justifyContent:'center', padding:'40px 20px' }}>
          <motion.div
            initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} transition={{ duration:.42, ease:[.22,1,.36,1] }}
            style={{ width:'100%', maxWidth:400 }}>

            <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:26 }}>
              <div style={{ width:38, height:38, borderRadius:11, background:'linear-gradient(135deg,#34d399,#0d9488)', display:'flex', alignItems:'center', justifyContent:'center', boxShadow:'0 3px 10px rgba(16,185,129,.25)' }}>
                <RiLineChartLine style={{ color:'#fff', fontSize:18 }}/>
              </div>
              <span style={{ fontWeight:800, fontSize:17, color:'#fff' }}>Market<span style={{ color:T.em }}>AI</span></span>
            </div>

            <h1 style={{ fontWeight:900, fontSize:26, color:'#fff', margin:'0 0 6px' }}>Sign in</h1>
            <p style={{ color:T.s5, fontSize:13.5, margin:'0 0 22px' }}>Access your dashboard and live market data.</p>

            {error && (
              <motion.div initial={{ opacity:0, y:-6 }} animate={{ opacity:1, y:0 }}
                style={{ marginBottom:16, display:'flex', alignItems:'center', gap:8, padding:'10px 14px', borderRadius:10, background:'rgba(239,68,68,.1)', border:'1px solid rgba(239,68,68,.2)', color:T.red, fontSize:13 }}>
                <span style={{ width:5, height:5, borderRadius:'50%', background:T.red, flexShrink:0 }}/>
                {error}
              </motion.div>
            )}

            <form onSubmit={handle}>
              <div style={{ marginBottom:14 }}>
                <label style={{ display:'block', fontSize:11, fontWeight:700, color:T.s4, textTransform:'uppercase', letterSpacing:'.07em', marginBottom:6 }}>Email Address</label>
                <div style={iconWrap}>
                  <RiMailLine style={iconLeft}/>
                  <input type="email" value={form.email} required
                    onChange={e => setForm(p => ({ ...p, email:e.target.value }))}
                    className="li-input" placeholder="you@example.com"/>
                </div>
              </div>

              <div style={{ marginBottom:20 }}>
                <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:6 }}>
                  <label style={{ fontSize:11, fontWeight:700, color:T.s4, textTransform:'uppercase', letterSpacing:'.07em' }}>Password</label>
                  <a href="#" style={{ fontSize:12, color:T.em }}>Forgot password?</a>
                </div>
                <div style={iconWrap}>
                  <RiLockPasswordLine style={iconLeft}/>
                  <input type={showPw ? 'text' : 'password'} value={form.password} required
                    onChange={e => setForm(p => ({ ...p, password:e.target.value }))}
                    className="li-input" placeholder="••••••••"/>
                  <button type="button" onClick={() => setShowPw(v => !v)} style={iconRight}>
                    {showPw ? <RiEyeOffLine/> : <RiEyeLine/>}
                  </button>
                </div>
              </div>

              <button type="submit" disabled={loading}
                style={{ width:'100%', display:'flex', alignItems:'center', justifyContent:'center', gap:8, padding:'12px', background:T.grad, color:'#fff', fontWeight:700, borderRadius:12, fontSize:15, border:'none', cursor:loading?'not-allowed':'pointer', opacity:loading?.7:1, transition:'opacity .2s', fontFamily:'inherit', boxShadow:'0 4px 14px rgba(16,185,129,.28)' }}>
                {loading
                  ? <div style={{ width:20, height:20, border:'2.5px solid rgba(255,255,255,.3)', borderTopColor:'#fff', borderRadius:'50%', animation:'li-spin 1s linear infinite' }}/>
                  : <><span>Sign In</span><RiArrowRightLine style={{ fontSize:16 }}/></>}
              </button>
            </form>

            <div style={{ display:'flex', alignItems:'center', gap:12, margin:'20px 0' }}>
              <div style={{ flex:1, height:1, background:'rgba(16,185,129,.12)' }}/>
              <span style={{ fontSize:11, color:T.s7, fontFamily:'monospace' }}>OR</span>
              <div style={{ flex:1, height:1, background:'rgba(16,185,129,.12)' }}/>
            </div>

            <p style={{ textAlign:'center', fontSize:13.5, color:T.s5, margin:'0 0 18px' }}>
              Don't have an account?{' '}
              <Link to="/register" style={{ color:T.em, fontWeight:700 }}>Create one free →</Link>
            </p>

            
          </motion.div>
        </div>
      </div>

      <style>{`
        @keyframes mai-pulse { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.5;transform:scale(1.5)} }
        @keyframes li-spin  { to { transform:rotate(360deg); } }
        @media (max-width: 991px) { #login-left { display:none!important; } }
      `}</style>
    </div>
  )
}