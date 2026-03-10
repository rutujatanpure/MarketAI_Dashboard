/**
 * Register.jsx — zero Tailwind, fully inline styles
 */
import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import Navbar from '../components/Navbar'
import {
  RiUserLine, RiMailLine, RiPhoneLine,
  RiLockPasswordLine, RiEyeLine, RiEyeOffLine,
  RiArrowRightLine, RiCheckLine, RiLineChartLine,
  RiShieldCheckLine, RiArrowUpLine, RiArrowDownLine,
  RiBrainLine, RiPulseLine, RiTimeLine,
} from 'react-icons/ri'
import toast from 'react-hot-toast'

function useRegisterStyles() {
  useEffect(() => {
    if (document.getElementById('reg-css')) return
    const s = document.createElement('style')
    s.id = 'reg-css'
    s.textContent = `
      @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700;800;900&display=swap');
      body { background:#070d07!important; margin:0; padding:0; font-family:'DM Sans',sans-serif!important; }
      .reg-input {
        width:100%; background:#0d1a0d;
        border:1px solid rgba(16,185,129,.18);
        border-radius:12px;
        padding:11px 42px 11px 40px;
        font-size:14px; color:#fff;
        outline:none; transition:border-color .18s;
        font-family:'DM Sans',sans-serif;
        box-sizing:border-box;
      }
      .reg-input:focus { border-color:rgba(16,185,129,.5); }
      .reg-input::placeholder { color:#475569; }
      .reg-input.match { border-color:rgba(16,185,129,.5); }
      .reg-input.mismatch { border-color:rgba(239,68,68,.5); }
      @keyframes reg-spin { to { transform:rotate(360deg); } }
      @keyframes reg-pulse { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.5;transform:scale(1.5)} }
      @media (max-width:991px) { #reg-left { display:none!important; } }
    `
    document.head.appendChild(s)
  }, [])
}

const T = {
  bg:'#070d07', card:'#0d1a0d',
  em:'#10b981', red:'#ef4444', amber:'#f59e0b',
  s4:'#94a3b8', s5:'#64748b', s6:'#475569', s7:'#334155',
  border:'rgba(16,185,129,.18)',
  grad:'linear-gradient(90deg,#10b981,#14b8a6)',
}

const RULES = [
  { test: v => v.length >= 8,       label:'8+ chars' },
  { test: v => /[A-Z]/.test(v),     label:'Uppercase' },
  { test: v => /[a-z]/.test(v),     label:'Lowercase' },
  { test: v => /\d/.test(v),        label:'Number' },
  { test: v => /[@$!%*?&]/.test(v), label:'Special' },
]

const HIGHLIGHTS = [
  { icon:RiPulseLine,       label:'Live data',   sub:'NSE, BSE & Binance feeds' },
  { icon:RiBrainLine,       label:'AI Signals',  sub:'Powered by Groq LLM' },
  { icon:RiTimeLine,        label:'<100ms',      sub:'WebSocket latency' },
  { icon:RiShieldCheckLine, label:'Secure',      sub:'JWT + bcrypt encryption' },
]

export default function Register() {
  useRegisterStyles()
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ username:'', email:'', mobileNumber:'', password:'', confirm:'' })
  const [showPw, setShowPw] = useState(false)
  const [showCf, setShowCf] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const set = (k, v) => setForm(p => ({ ...p, [k]:v }))

  const validate = () => {
    if (form.username.length < 3)                        return 'Username must be at least 3 characters'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return 'Invalid email address'
    if (!/^[6-9]\d{9}$/.test(form.mobileNumber))        return 'Enter a valid 10-digit Indian mobile number'
    if (!RULES.every(r => r.test(form.password)))        return 'Password does not meet all requirements'
    if (form.password !== form.confirm)                  return 'Passwords do not match'
    return null
  }

  const handle = async e => {
    e.preventDefault()
    const err = validate()
    if (err) { setError(err); return }
    setError(''); setLoading(true)
    try {
      await register({ username:form.username, email:form.email, mobileNumber:form.mobileNumber, password:form.password,confirmPassword: form.confirm  })
      toast.success('Account created! Welcome to MarketAI 🎉')
      navigate('/dashboard')
    } catch (e) {
      setError(e.response?.data?.message || 'Registration failed. Please try again.')
    } finally { setLoading(false) }
  }

  const strength = RULES.filter(r => r.test(form.password)).length
  const strColor = ['','#ef4444','#f59e0b','#f59e0b','#10b981','#10b981'][strength]
  const strLabel = ['','Weak','Fair','Good','Strong','Very Strong'][strength]

  const iconLeft  = { position:'absolute', left:12, top:'50%', transform:'translateY(-50%)', color:T.s5, fontSize:16, pointerEvents:'none' }
  const iconRight = { position:'absolute', right:12, top:'50%', transform:'translateY(-50%)', color:T.s5, background:'none', border:'none', cursor:'pointer', display:'flex', alignItems:'center', fontSize:16 }

  const Label = ({ children }) => (
    <label style={{ display:'block', fontSize:11, fontWeight:700, color:T.s4, textTransform:'uppercase', letterSpacing:'.07em', marginBottom:6 }}>
      {children}
    </label>
  )

  return (
    <div style={{ minHeight:'100vh', background:T.bg, fontFamily:"'DM Sans',sans-serif", overflowX:'hidden' }}>
      <Navbar/>

      <div style={{ minHeight:'100vh', paddingTop:64, display:'flex' }}>

        {/* ── Left panel (lg only) ── */}
        <div id="reg-left" style={{
          width:'48%', flexShrink:0,
          background:'linear-gradient(135deg,#0a1a0a 0%,#0d2b1a 60%,#071a10 100%)',
          position:'relative', overflow:'hidden',
          display:'flex', flexDirection:'column',
          justifyContent:'space-between',
          padding:'48px 44px',
        }}>
          <div style={{ position:'absolute', inset:0, opacity:.04, backgroundImage:'linear-gradient(#10b981 1px,transparent 1px),linear-gradient(90deg,#10b981 1px,transparent 1px)', backgroundSize:'48px 48px', pointerEvents:'none' }}/>
          <div style={{ position:'absolute', top:'30%', right:'25%', width:260, height:260, background:'rgba(16,185,129,.09)', borderRadius:'50%', filter:'blur(44px)', pointerEvents:'none' }}/>

          {/* Top */}
          <motion.div initial={{ opacity:0, x:-16 }} animate={{ opacity:1, x:0 }} transition={{ delay:.1 }} style={{ position:'relative', zIndex:2 }}>
            <div style={{ display:'inline-flex', alignItems:'center', gap:7, padding:'5px 13px', borderRadius:40, border:'1px solid rgba(16,185,129,.2)', background:'rgba(16,185,129,.08)', marginBottom:22 }}>
              <span style={{ width:6, height:6, borderRadius:'50%', background:T.em, display:'inline-block', animation:'reg-pulse 2s ease-in-out infinite' }}/>
              <span style={{ fontSize:10, fontFamily:'monospace', color:T.em, fontWeight:700 }}>Free forever · No credit card</span>
            </div>
            <h2 style={{ fontWeight:900, fontSize:'clamp(24px,3vw,36px)', color:'#fff', lineHeight:1.15, margin:'0 0 12px', letterSpacing:'-.02em' }}>
              Join thousands of<br/>
              <span style={{ background:'linear-gradient(90deg,#34d399,#5eead4)', WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent', backgroundClip:'text' }}>smart traders</span>
            </h2>
            <p style={{ color:T.s4, fontSize:13.5, lineHeight:1.65, maxWidth:300, margin:0 }}>
              Get real-time NSE, BSE & crypto data with AI-powered insights. Set up in under 2 minutes.
            </p>
          </motion.div>

          {/* Highlights grid */}
          <motion.div initial={{ opacity:0, y:16 }} animate={{ opacity:1, y:0 }} transition={{ delay:.24 }} style={{ position:'relative', zIndex:2 }}>
            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:10 }}>
              {HIGHLIGHTS.map((h,i) => (
                <motion.div key={h.label} initial={{ opacity:0, y:8 }} animate={{ opacity:1, y:0 }} transition={{ delay:.3+i*.07 }}
                  style={{ padding:'14px', background:'rgba(10,26,10,.8)', border:'1px solid rgba(16,185,129,.15)', borderRadius:14 }}>
                  <div style={{ width:36, height:36, borderRadius:10, background:'rgba(16,185,129,.15)', border:'1px solid rgba(16,185,129,.2)', display:'flex', alignItems:'center', justifyContent:'center', marginBottom:10 }}>
                    <h.icon style={{ color:T.em, fontSize:15 }}/>
                  </div>
                  <p style={{ fontSize:13, fontWeight:700, color:'#fff', margin:'0 0 2px' }}>{h.label}</p>
                  <p style={{ fontSize:11, color:T.s5, margin:0 }}>{h.sub}</p>
                </motion.div>
              ))}
            </div>
          </motion.div>

          {/* Testimonial */}
          <motion.div initial={{ opacity:0, y:12 }} animate={{ opacity:1, y:0 }} transition={{ delay:.5 }} style={{ position:'relative', zIndex:2 }}>
            <div style={{ padding:'16px', background:'rgba(10,26,10,.8)', border:'1px solid rgba(16,185,129,.15)', borderRadius:14 }}>
              <div style={{ display:'flex', gap:3, marginBottom:8 }}>
                {[...Array(5)].map((_,i) => <span key={i} style={{ color:T.amber, fontSize:12 }}>★</span>)}
              </div>
              <p style={{ fontSize:13, color:'#cbd5e1', lineHeight:1.6, fontStyle:'italic', margin:'0 0 8px' }}>
                "The AI signals are incredibly accurate. MarketAI changed how I analyze the markets."
              </p>
              <p style={{ fontSize:11, color:T.s5, fontWeight:600, margin:0 }}>— Rahul K., NSE trader</p>
            </div>
          </motion.div>
        </div>

        {/* ── Right form ── */}
        <div style={{ flex:1, display:'flex', alignItems:'flex-start', justifyContent:'center', padding:'40px 20px', overflowY:'auto' }}>
          <motion.div
            initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} transition={{ duration:.42, ease:[.22,1,.36,1] }}
            style={{ width:'100%', maxWidth:400 }}>

            {/* Logo */}
            <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:24 }}>
              <div style={{ width:38, height:38, borderRadius:11, background:'linear-gradient(135deg,#34d399,#0d9488)', display:'flex', alignItems:'center', justifyContent:'center' }}>
                <RiLineChartLine style={{ color:'#fff', fontSize:18 }}/>
              </div>
              <span style={{ fontWeight:800, fontSize:17, color:'#fff' }}>Market<span style={{ color:T.em }}>AI</span></span>
            </div>

            <h1 style={{ fontWeight:900, fontSize:24, color:'#fff', margin:'0 0 5px' }}>Create account</h1>
            <p style={{ color:T.s5, fontSize:13.5, margin:'0 0 20px' }}>Free forever. Live markets in 2 minutes.</p>

            {/* Error */}
            {error && (
              <motion.div initial={{ opacity:0, y:-6 }} animate={{ opacity:1, y:0 }}
                style={{ marginBottom:14, display:'flex', alignItems:'flex-start', gap:8, padding:'10px 14px', borderRadius:10, background:'rgba(239,68,68,.1)', border:'1px solid rgba(239,68,68,.2)', color:T.red, fontSize:13 }}>
                <span style={{ width:5, height:5, borderRadius:'50%', background:T.red, flexShrink:0, marginTop:5 }}/>
                {error}
              </motion.div>
            )}
            {error && error.toLowerCase().includes("registered") && (
  <div style={{ marginBottom:14 }}>
    <button
      onClick={() => navigate('/login')}
      style={{
        padding:'8px 14px',
        borderRadius:8,
        border:'none',
        cursor:'pointer',
        background:'#396e59',
        color:'#fff',
        fontSize:13
      }}
    >
      Go to Login
    </button>
  </div>
)}

            <form onSubmit={handle}>
              {/* Username */}
              <div style={{ marginBottom:12 }}>
                <Label>Username</Label>
                <div style={{ position:'relative' }}>
                  <RiUserLine style={iconLeft}/>
                  <input value={form.username} required onChange={e => set('username', e.target.value)}
                    className="reg-input" placeholder="yourname"/>
                </div>
              </div>

              {/* Email */}
              <div style={{ marginBottom:12 }}>
                <Label>Email Address</Label>
                <div style={{ position:'relative' }}>
                  <RiMailLine style={iconLeft}/>
                  <input type="email" value={form.email} required onChange={e => set('email', e.target.value)}
                    className="reg-input" placeholder="you@example.com"/>
                </div>
              </div>

              {/* Mobile */}
              <div style={{ marginBottom:12 }}>
                <Label>Mobile Number</Label>
                <div style={{ position:'relative' }}>
                  {/* +91 prefix badge */}
                  <div style={{ position:'absolute', left:0, top:0, bottom:0, display:'flex', alignItems:'center', padding:'0 12px', borderRight:'1px solid rgba(16,185,129,.18)', pointerEvents:'none' }}>
                    <span style={{ fontSize:12, fontFamily:'monospace', color:T.s5 }}>+91</span>
                  </div>
                  <input value={form.mobileNumber} required maxLength={10}
                    onChange={e => set('mobileNumber', e.target.value.replace(/\D/,''))}
                    className="reg-input" style={{ paddingLeft:52 }}
                    placeholder="9876543210"/>
                </div>
              </div>

              {/* Password */}
              <div style={{ marginBottom:12 }}>
                <Label>Password</Label>
                <div style={{ position:'relative' }}>
                  <RiLockPasswordLine style={iconLeft}/>
                  <input type={showPw ? 'text' : 'password'} value={form.password} required
                    onChange={e => set('password', e.target.value)}
                    className="reg-input" placeholder="Create a strong password"/>
                  <button type="button" onClick={() => setShowPw(v => !v)} style={iconRight}>
                    {showPw ? <RiEyeOffLine/> : <RiEyeLine/>}
                  </button>
                </div>

                {/* Strength meter */}
                {form.password && (
                  <div style={{ marginTop:8 }}>
                    <div style={{ display:'flex', gap:4, marginBottom:4 }}>
                      {[1,2,3,4,5].map(i => (
                        <div key={i} style={{ height:3, flex:1, borderRadius:40, transition:'background .3s', background: i <= strength ? strColor : '#1a2e1a' }}/>
                      ))}
                    </div>
                    <span style={{ fontSize:10, fontFamily:'monospace', fontWeight:700, color:strColor }}>{strLabel}</span>
                  </div>
                )}

                {/* Rule pills */}
                <div style={{ display:'flex', flexWrap:'wrap', gap:5, marginTop:8 }}>
                  {RULES.map(r => {
                    const ok = r.test(form.password)
                    return (
                      <span key={r.label} style={{
                        display:'inline-flex', alignItems:'center', gap:3,
                        fontSize:9, fontFamily:'monospace', padding:'2px 7px', borderRadius:20,
                        border:`1px solid ${ok ? 'rgba(16,185,129,.3)' : '#1e293b'}`,
                        background: ok ? 'rgba(16,185,129,.1)' : 'transparent',
                        color: ok ? T.em : T.s7,
                        transition:'all .2s',
                      }}>
                        {ok && <RiCheckLine style={{ fontSize:8 }}/>}
                        {r.label}
                      </span>
                    )
                  })}
                </div>
              </div>

              {/* Confirm */}
              <div style={{ marginBottom:18 }}>
                <Label>Confirm Password</Label>
                <div style={{ position:'relative' }}>
                  <RiLockPasswordLine style={iconLeft}/>
                  <input type={showCf ? 'text' : 'password'} value={form.confirm} required
                    onChange={e => set('confirm', e.target.value)}
                    className={`reg-input${form.confirm ? (form.confirm === form.password ? ' match' : ' mismatch') : ''}`}
                    placeholder="••••••••"/>
                  <button type="button" onClick={() => setShowCf(v => !v)} style={iconRight}>
                    {showCf ? <RiEyeOffLine/> : <RiEyeLine/>}
                  </button>
                </div>
                {form.confirm && form.confirm !== form.password && (
                  <p style={{ fontSize:11, color:T.red, margin:'5px 0 0' }}>Passwords don't match</p>
                )}
                {form.confirm && form.confirm === form.password && form.password && (
                  <p style={{ fontSize:11, color:T.em, margin:'5px 0 0', display:'flex', alignItems:'center', gap:4 }}>
                    <RiCheckLine/>Passwords match
                  </p>
                )}
              </div>

              {/* Submit */}
              <button type="submit" disabled={loading}
                style={{ width:'100%', display:'flex', alignItems:'center', justifyContent:'center', gap:8, padding:'12px', background:T.grad, color:'#fff', fontWeight:700, borderRadius:12, fontSize:15, border:'none', cursor:loading?'not-allowed':'pointer', opacity:loading?.7:1, fontFamily:'inherit', boxShadow:'0 4px 14px rgba(16,185,129,.28)', transition:'opacity .2s' }}>
                {loading
                  ? <div style={{ width:20, height:20, border:'2.5px solid rgba(255,255,255,.3)', borderTopColor:'#fff', borderRadius:'50%', animation:'reg-spin 1s linear infinite' }}/>
                  : <><span>Create Free Account</span><RiArrowRightLine style={{ fontSize:16 }}/></>}
              </button>
            </form>

            <p style={{ textAlign:'center', fontSize:13.5, color:T.s5, margin:'16px 0 8px' }}>
              Already have an account?{' '}
              <Link to="/login" style={{ color:T.em, fontWeight:700 }}>Sign in →</Link>
            </p>
            <p style={{ textAlign:'center', fontSize:10, color:T.s7, fontFamily:'monospace', margin:0 }}>
              By creating an account you agree to our Terms & Privacy Policy
            </p>
          </motion.div>
        </div>
      </div>
    </div>
  )
}