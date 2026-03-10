/**
 * Home.jsx — Compact hero, 2-col info sections, no bottom CTA
 */
import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Navbar from '../components/Navbar'
import {
  RiLineChartLine, RiBrainLine, RiShieldCheckLine, RiArrowRightLine,
  RiStockLine, RiPulseLine, RiBarChart2Line, RiNotification3Line,
  RiCheckLine, RiGlobalLine, RiTimeLine,
  RiArrowUpLine, RiArrowDownLine, RiHeartLine, RiHeartFill,
  RiFlashlightLine, RiTrophyLine, RiTeamLine,
  RiAlertLine, RiRadarLine, RiDashboardLine,
  RiBookmarkLine, RiSettings3Line,
  RiLightbulbLine, RiSpeedLine, RiLockLine,
} from 'react-icons/ri'

let stylesInjected = false
function InjectStyles() {
  useEffect(() => {
    if (stylesInjected) return
    stylesInjected = true
    const bs = document.createElement('link')
    bs.rel = 'stylesheet'
    bs.href = 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css'
    document.head.appendChild(bs)
    const gf = document.createElement('link')
    gf.rel = 'stylesheet'
    gf.href = 'https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;600;700;800;900&family=DM+Mono:wght@400;500&display=swap'
    document.head.appendChild(gf)
    const s = document.createElement('style')
    s.textContent = `
      *,*::before,*::after{box-sizing:border-box}
      body{background:#060c06!important;color:#f1f5f9!important;font-family:'DM Sans',sans-serif!important;overflow-x:hidden;margin:0;padding:0}
      a{text-decoration:none!important}
      @keyframes tick{from{transform:translateX(0)}to{transform:translateX(-50%)}}
      .tick{display:inline-flex;white-space:nowrap;animation:tick 40s linear infinite}
      .tick:hover{animation-play-state:paused}
      @keyframes blink{0%,100%{opacity:1}50%{opacity:.25}}
      .blink{animation:blink 2s ease-in-out infinite}
      .gc{transition:transform .18s,border-color .18s}
      .gc:hover{transform:translateY(-3px);border-color:rgba(16,185,129,.38)!important}
      .gb{transition:opacity .15s,transform .15s!important}
      .gb:hover{opacity:.85!important;transform:scale(1.02)!important}
      .gr{background:linear-gradient(90deg,#34d399,#5eead4);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}
      .divider{width:1px;background:rgba(16,185,129,.12);align-self:stretch}
      ::-webkit-scrollbar{width:3px}
      ::-webkit-scrollbar-thumb{background:#1a2e1a;border-radius:2px}
    `
    document.head.appendChild(s)
  }, [])
  return null
}

const T = {
  bg:'#060c06', bg2:'#091209', card:'#0c180c', card2:'#0e1a0e',
  em:'#10b981', teal:'#14b8a6', amber:'#f59e0b', red:'#ef4444', indigo:'#818cf8',
  s3:'#cbd5e1', s4:'#94a3b8', s5:'#64748b', s6:'#475569', s7:'#334155',
  border:'rgba(16,185,129,.14)',
  grad:'linear-gradient(135deg,#10b981,#14b8a6)',
}

const TICKER = [
  {sym:'RELIANCE',price:'₹2,987',chg:'+1.24%',up:true},
  {sym:'TCS',price:'₹4,121',chg:'+0.87%',up:true},
  {sym:'HDFCBANK',price:'₹1,672',chg:'-0.34%',up:false},
  {sym:'INFY',price:'₹1,489',chg:'+2.11%',up:true},
  {sym:'BTC',price:'$67,420',chg:'+2.34%',up:true},
  {sym:'ETH',price:'$3,512',chg:'+1.87%',up:true},
  {sym:'SOL',price:'$178.5',chg:'-0.92%',up:false},
  {sym:'ADANIENT',price:'₹2,841',chg:'-1.23%',up:false},
  {sym:'NVDA',price:'$924.1',chg:'+3.76%',up:true},
  {sym:'SBIN',price:'₹812',chg:'+0.56%',up:true},
]

const TRENDING = [
  {sym:'RELIANCE',name:'Reliance Industries',price:'₹2,987.40',chg:'+1.24%',up:true, likes:3241,sector:'Energy', pts:'0,40 16,35 32,28 48,20 64,15 80,10 96,6'},
  {sym:'TCS',     name:'Tata Consultancy',   price:'₹4,121.00',chg:'+0.87%',up:true, likes:2891,sector:'IT',     pts:'0,38 16,32 32,29 48,22 64,18 80,14 96,8'},
  {sym:'HDFCBANK',name:'HDFC Bank',          price:'₹1,672.15',chg:'-0.34%',up:false,likes:1987,sector:'Finance',pts:'0,10 16,14 32,18 48,22 64,26 80,32 96,38'},
  {sym:'BTC',     name:'Bitcoin',            price:'$67,420',  chg:'+2.34%',up:true, likes:5823,sector:'Crypto', pts:'0,42 16,36 32,30 48,22 64,16 80,9 96,4'},
  {sym:'NVDA',    name:'NVIDIA Corp',        price:'$924.10',  chg:'+3.76%',up:true, likes:4512,sector:'Tech',   pts:'0,44 16,38 32,28 48,20 64,12 80,7 96,2'},
  {sym:'INFY',    name:'Infosys Ltd',        price:'₹1,489.60',chg:'+2.11%',up:true, likes:1654,sector:'IT',     pts:'0,40 16,33 32,27 48,18 64,14 80,9 96,5'},
]

const ALERTS = [
  {sym:'PEPE',     type:'PUMP',      msg:'Volume spike 847% above 20-day avg', sev:'HIGH',   time:'2m ago', up:true},
  {sym:'SHIBUSDT', type:'DUMP',      msg:'Flash crash −18% in 4 minutes',       sev:'HIGH',   time:'7m ago', up:false},
  {sym:'ADANIENT', type:'BREAKOUT',  msg:'Breaking 52-week resistance ₹2,900',  sev:'MEDIUM', time:'12m ago',up:true},
  {sym:'BONK',     type:'WHALE',     msg:'4.2B BONK moved to exchange',          sev:'HIGH',   time:'18m ago',up:false},
  {sym:'RELIANCE', type:'AI SIGNAL', msg:'Strong BUY — RSI 34 oversold bounce', sev:'LOW',    time:'22m ago',up:true},
  {sym:'HDFCBANK', type:'ANOMALY',   msg:'Unusual options activity detected',    sev:'MEDIUM', time:'31m ago',up:false},
]

const sevColor  = {HIGH:T.red, MEDIUM:T.amber, LOW:T.em}
const typeColor = {PUMP:T.em, DUMP:T.red, BREAKOUT:T.teal, WHALE:T.amber, 'AI SIGNAL':T.indigo, ANOMALY:T.amber}

/* ── What you can do — left column ── */
const PLATFORM_COLS = {
  left: [
    {icon:RiDashboardLine,  color:T.em,     title:'Market Dashboard',   pts:['Live Nifty 50 + 50 crypto prices','NSE/BSE session status & hours','Top gainers & losers real-time','Sector performance heatmap']},
    {icon:RiBarChart2Line,  color:T.teal,   title:'Advanced Charts',    pts:['Candlestick, line & depth charts','RSI · MACD · Bollinger Bands · ATR','1m / 5m / 1h / 1D / 1W timeframes','Volume + Z-Score anomaly overlay']},
    {icon:RiBrainLine,      color:T.amber,  title:'Gemini AI Signals',  pts:['BUY / SELL / HOLD per asset','Confidence score 0–100%','Today / Week / Month predictions','Plain-English AI summary']},
  ],
  right: [
    {icon:RiAlertLine,      color:T.red,    title:'Anomaly Alerts',     pts:['Pump & dump detection live','Whale wallet movement alerts','Volume spike >500% detection','Risk score 0–100 per asset']},
    {icon:RiBookmarkLine,   color:T.indigo, title:'Watchlist',          pts:['Save any NSE stock or crypto','Crypto & Stocks separate tabs','Live price & % change per item','1-click chart view from list']},
    {icon:RiSettings3Line,  color:T.s4,     title:'Profile & Settings', pts:['Edit name, email, currency','USD / INR display switch','Enable email notifications','Role: USER or ADMIN · JWT 7d']},
  ],
}

/* Sparkline */
function Spark({pts, up}) {
  const c = up ? T.em : T.red
  return (
    <div style={{height:26,width:'100%',overflow:'hidden',position:'relative'}}>
      <svg viewBox="0 0 96 48" style={{position:'absolute',inset:0,width:'100%',height:'100%'}} preserveAspectRatio="none">
        <defs>
          <linearGradient id={`g${up?'u':'d'}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={c} stopOpacity=".18"/>
            <stop offset="100%" stopColor={c} stopOpacity="0"/>
          </linearGradient>
        </defs>
        <polyline points={pts} fill="none" stroke={c} strokeWidth="2" strokeLinecap="round"/>
        <polygon points={`${pts} 96,48 0,48`} fill={`url(#g${up?'u':'d'})`}/>
      </svg>
    </div>
  )
}

/* Stock Card */
function StockCard({stock}) {
  const [liked, setLiked] = useState(false)
  const [likes, setLikes] = useState(stock.likes)
  const toggle = () => { setLiked(v=>!v); setLikes(v => liked ? v-1 : v+1) }
  const c = stock.up ? T.em : T.red
  return (
    <div className="gc" style={{background:T.card,border:`1px solid ${T.border}`,borderRadius:14,padding:14,height:'100%',cursor:'pointer'}}>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',marginBottom:8}}>
        <div>
          <div style={{display:'flex',alignItems:'center',gap:5,marginBottom:2}}>
            <span style={{fontWeight:800,color:'#fff',fontSize:13}}>{stock.sym}</span>
            <span style={{fontSize:9,fontFamily:'DM Mono,monospace',padding:'1px 5px',borderRadius:20,background:'rgba(255,255,255,.05)',color:T.s5,border:`1px solid ${T.border}`}}>{stock.sector}</span>
          </div>
          <p style={{fontSize:10,color:T.s5,margin:0}}>{stock.name}</p>
        </div>
        <button onClick={toggle} style={{display:'flex',alignItems:'center',gap:3,padding:'3px 7px',borderRadius:7,fontSize:10,fontWeight:600,cursor:'pointer',background:liked?'rgba(239,68,68,.1)':'rgba(255,255,255,.04)',color:liked?'#f87171':T.s5,border:`1px solid ${liked?'rgba(239,68,68,.18)':'transparent'}`,fontFamily:'inherit'}}>
          {liked?<RiHeartFill style={{fontSize:10}}/>:<RiHeartLine style={{fontSize:10}}/>}{likes.toLocaleString()}
        </button>
      </div>
      <p style={{fontSize:17,fontWeight:900,color:'#fff',margin:'0 0 2px',fontVariantNumeric:'tabular-nums'}}>{stock.price}</p>
      <p style={{fontSize:11,fontWeight:700,color:c,display:'flex',alignItems:'center',gap:2,margin:'0 0 7px'}}>
        {stock.up?<RiArrowUpLine/>:<RiArrowDownLine/>}{stock.chg}
      </p>
      <Spark pts={stock.pts} up={stock.up}/>
    </div>
  )
}

/* Section label */
const SLabel = ({icon:Icon, color, text}) => (
  <div style={{display:'flex',alignItems:'center',gap:5,marginBottom:6}}>
    <Icon style={{color,fontSize:12}}/>
    <span style={{fontSize:9,fontFamily:'DM Mono,monospace',color,fontWeight:700,textTransform:'uppercase',letterSpacing:'.1em'}}>{text}</span>
  </div>
)

/* ══ HOME ══ */
export default function Home() {
  const { isAuth } = useAuth()
  const doubled = [...TICKER, ...TICKER]

  return (
    <div style={{minHeight:'100vh',background:T.bg,color:'#f1f5f9',fontFamily:"'DM Sans',sans-serif",overflowX:'hidden'}}>
      <InjectStyles/>
      <Navbar/>

      {/* Ticker */}
      <div style={{position:'fixed',top:64,left:0,right:0,zIndex:40,height:30,background:'rgba(6,12,6,.97)',borderBottom:`1px solid ${T.border}`,display:'flex',alignItems:'center',overflow:'hidden'}}>
        <div className="tick">
          {doubled.map((item,i) => (
            <span key={i} style={{display:'inline-flex',alignItems:'center',gap:4,padding:'0 10px',fontSize:9.5,fontFamily:'DM Mono,monospace',userSelect:'none'}}>
              <span style={{color:T.s4,fontWeight:700}}>{item.sym}</span>
              <span style={{color:T.s3}}>{item.price}</span>
              <span style={{color:item.up?T.em:T.red,display:'flex',alignItems:'center',gap:1}}>
                {item.up?<RiArrowUpLine style={{fontSize:9}}/>:<RiArrowDownLine style={{fontSize:9}}/>}{item.chg}
              </span>
              <span style={{color:'#1e293b'}}>·</span>
            </span>
          ))}
        </div>
      </div>

      {/* ══ HERO — compact 2-col split ══ */}
      <section style={{paddingTop:108,paddingBottom:48,paddingLeft:20,paddingRight:20,position:'relative',overflow:'hidden'}}>
        {/* subtle bg glow */}
        <div style={{position:'absolute',top:'30%',left:'30%',width:500,height:400,background:'rgba(5,55,40,.22)',borderRadius:'50%',filter:'blur(72px)',pointerEvents:'none'}}/>

        <div className="container-xl" style={{position:'relative',zIndex:2}}>
          <div className="row align-items-center g-4">

            {/* LEFT — headline + CTAs (compact) */}
            <div className="col-12 col-lg-5">
              {/* Live badge */}
              <div style={{marginBottom:14}}>
                <span style={{display:'inline-flex',alignItems:'center',gap:5,padding:'3px 12px',borderRadius:40,border:'1px solid rgba(16,185,129,.22)',background:'rgba(16,185,129,.06)',fontSize:10,fontFamily:'DM Mono,monospace',fontWeight:700,color:T.em}}>
                  <span className="blink" style={{width:5,height:5,borderRadius:'50%',background:T.em,display:'inline-block'}}/>
                  NSE · BSE · Binance · Gemini AI
                </span>
              </div>

              {/* Heading — small & tight */}
              <h1 style={{fontWeight:900,fontSize:'clamp(22px,3.2vw,36px)',lineHeight:1.12,marginBottom:10,letterSpacing:'-.03em'}}>
                <span style={{color:'#fff',display:'block'}}>Markets &amp; Crypto</span>
                <span className="gr" style={{display:'block'}}>AI Intelligence</span>
              </h1>

              {/* Sub — 1 line only */}
              <p style={{color:T.s4,fontSize:13,lineHeight:1.6,marginBottom:18,maxWidth:340}}>
                Live NSE, BSE &amp; Binance prices. Gemini AI signals. Anomaly alerts. One dashboard.
              </p>

              {/* 3 trust points inline */}
              <div style={{display:'flex',gap:12,flexWrap:'wrap',marginBottom:20}}>
                {[
                  {icon:RiSpeedLine,    text:'Sub-100ms live feed'},
                  {icon:RiBrainLine,    text:'Gemini AI daily signals'},
                  {icon:RiLockLine,     text:'JWT secured, free'},
                ].map(({icon:Icon,text}) => (
                  <span key={text} style={{display:'flex',alignItems:'center',gap:4,fontSize:11,color:T.s5}}>
                    <Icon style={{color:T.em,fontSize:13}}/>{text}
                  </span>
                ))}
              </div>

              {/* CTAs */}
              <div style={{display:'flex',gap:8,flexWrap:'wrap'}}>
                {isAuth ? (
                  <Link to="/dashboard" className="gb"
                    style={{display:'inline-flex',alignItems:'center',gap:5,padding:'9px 20px',background:T.grad,color:'#fff',fontWeight:800,borderRadius:10,fontSize:13,boxShadow:'0 3px 14px rgba(16,185,129,.28)'}}>
                    <RiDashboardLine/> Dashboard
                  </Link>
                ) : (
                  <>
                    <Link to="/register" className="gb"
                      style={{display:'inline-flex',alignItems:'center',gap:5,padding:'9px 20px',background:T.grad,color:'#fff',fontWeight:800,borderRadius:10,fontSize:13,boxShadow:'0 3px 14px rgba(16,185,129,.28)'}}>
                      Start Free <RiArrowRightLine/>
                    </Link>
                    <Link to="/login" className="gb"
                      style={{display:'inline-flex',alignItems:'center',gap:5,padding:'9px 18px',background:'rgba(255,255,255,.05)',color:'#e2e8f0',fontWeight:600,borderRadius:10,border:'1px solid rgba(255,255,255,.09)',fontSize:13}}>
                      Sign in
                    </Link>
                  </>
                )}
              </div>
            </div>

            {/* RIGHT — live mini dashboard preview */}
            <div className="col-12 col-lg-7">
              <div style={{background:T.card,border:`1px solid ${T.border}`,borderRadius:16,overflow:'hidden',boxShadow:'0 16px 48px rgba(0,0,0,.5)'}}>
                {/* Browser bar */}
                <div style={{background:T.card2,padding:'9px 14px',borderBottom:`1px solid ${T.border}`,display:'flex',alignItems:'center',gap:8}}>
                  <div style={{display:'flex',gap:4}}>
                    {['#ef4444','#f59e0b','#10b981'].map((c,i)=><div key={i} style={{width:8,height:8,borderRadius:'50%',background:c,opacity:.6}}/>)}
                  </div>
                  <div style={{flex:1,textAlign:'center'}}>
                    <span style={{fontSize:9,fontFamily:'DM Mono,monospace',color:T.s7}}>marketai.app/dashboard</span>
                  </div>
                  <div style={{display:'flex',alignItems:'center',gap:4}}>
                    <span className="blink" style={{width:5,height:5,borderRadius:'50%',background:T.em,display:'inline-block'}}/>
                    <span style={{fontSize:8,fontFamily:'DM Mono,monospace',color:T.em,fontWeight:700}}>LIVE</span>
                  </div>
                </div>
                {/* Mini price row */}
                <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:1,background:T.border,padding:0}}>
                  {[
                    {sym:'RELIANCE',price:'₹2,987',chg:'+1.24%',up:true},
                    {sym:'TCS',     price:'₹4,121',chg:'+0.87%',up:true},
                    {sym:'BTC',     price:'$67.4K', chg:'+2.34%',up:true},
                    {sym:'ETH',     price:'$3,512', chg:'-0.92%',up:false},
                  ].map(s => (
                    <div key={s.sym} style={{background:T.card,padding:'10px 12px'}}>
                      <p style={{fontSize:8,fontFamily:'DM Mono,monospace',color:T.s5,margin:'0 0 2px'}}>{s.sym}</p>
                      <p style={{fontSize:12,fontWeight:800,color:'#fff',margin:'0 0 1px',fontVariantNumeric:'tabular-nums'}}>{s.price}</p>
                      <p style={{fontSize:8,color:s.up?T.em:T.red,fontWeight:700,display:'flex',alignItems:'center',gap:1,margin:0}}>
                        {s.up?<RiArrowUpLine style={{fontSize:8}}/>:<RiArrowDownLine style={{fontSize:8}}/>}{s.chg}
                      </p>
                    </div>
                  ))}
                </div>
                {/* Chart mock */}
                <div style={{padding:'12px 14px',borderBottom:`1px solid ${T.border}`}}>
                  <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:8}}>
                    <span style={{fontSize:10,fontFamily:'DM Mono,monospace',color:T.s4,fontWeight:700}}>RELIANCE · NSE</span>
                    <div style={{display:'flex',gap:3}}>
                      {['1D','1W','1M'].map((t,i)=>(
                        <span key={t} style={{fontSize:8,fontFamily:'DM Mono,monospace',padding:'2px 6px',borderRadius:5,background:i===0?'rgba(16,185,129,.14)':'transparent',color:i===0?T.em:T.s7,border:i===0?'1px solid rgba(16,185,129,.22)':'1px solid transparent',fontWeight:700}}>{t}</span>
                      ))}
                    </div>
                  </div>
                  <div style={{height:64,position:'relative',overflow:'hidden'}}>
                    <svg viewBox="0 0 400 60" style={{position:'absolute',inset:0,width:'100%',height:'100%'}} preserveAspectRatio="none">
                      <defs><linearGradient id="cg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#10b981" stopOpacity=".22"/><stop offset="100%" stopColor="#10b981" stopOpacity="0"/></linearGradient></defs>
                      <path d="M0,50 L40,44 L80,46 L120,36 L160,30 L200,24 L240,18 L280,13 L320,9 L360,6 L400,4" fill="none" stroke="#10b981" strokeWidth="2" strokeLinecap="round"/>
                      <path d="M0,50 L40,44 L80,46 L120,36 L160,30 L200,24 L240,18 L280,13 L320,9 L360,6 L400,4 L400,60 L0,60Z" fill="url(#cg)"/>
                      <circle cx="390" cy="5" r="3" fill="#10b981"/><circle cx="390" cy="5" r="6" fill="#10b981" fillOpacity=".15"/>
                    </svg>
                  </div>
                </div>
                {/* AI signal bar */}
                <div style={{padding:'9px 14px',display:'flex',alignItems:'center',gap:8,background:'rgba(16,185,129,.05)'}}>
                  <RiBrainLine style={{color:T.em,fontSize:14,flexShrink:0}}/>
                  <div style={{flex:1}}>
                    <span style={{fontSize:10,fontWeight:700,color:'#a7f3d0'}}>AI: </span>
                    <span style={{fontSize:10,fontWeight:900,color:T.em}}>STRONG BUY</span>
                    <span style={{fontSize:9,color:T.s5,marginLeft:8,fontFamily:'DM Mono,monospace'}}>Conf. 87% · Gemini</span>
                  </div>
                  <span style={{fontSize:8,color:T.s7,fontFamily:'DM Mono,monospace'}}>just now</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ══ STATS — slim bar ══ */}
      <section style={{background:T.bg2,padding:'18px 20px',borderTop:`1px solid ${T.border}`,borderBottom:`1px solid ${T.border}`}}>
        <div className="container" style={{maxWidth:680}}>
          <div style={{display:'flex',justifyContent:'space-around',flexWrap:'wrap',gap:12}}>
            {[
              {v:'100+',l:'Live Assets',icon:RiStockLine},
              {v:'<100ms',l:'Latency',icon:RiTimeLine},
              {v:'80+',l:'Symbols tracked',icon:RiGlobalLine},
              {v:'24/7',l:'Monitoring',icon:RiPulseLine},
              {v:'1',l:'AI call/day',icon:RiBrainLine},
            ].map(s => (
              <div key={s.l} style={{display:'flex',alignItems:'center',gap:7}}>
                <s.icon style={{color:T.em,fontSize:16}}/>
                <div>
                  <p style={{fontWeight:900,fontSize:16,color:T.em,margin:0,fontVariantNumeric:'tabular-nums'}}>{s.v}</p>
                  <p style={{fontSize:10,color:T.s5,margin:0}}>{s.l}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ══ PLATFORM — 2 columns of feature lists ══ */}
      <section style={{background:T.bg,padding:'52px 20px'}}>
        <div className="container-xl">
          <div style={{textAlign:'center',marginBottom:28}}>
            <SLabel icon={RiFlashlightLine} color={T.em} text="What's Inside"/>
            <h2 style={{fontWeight:900,fontSize:'clamp(17px,2.2vw,24px)',color:'#fff',margin:'0 0 6px',letterSpacing:'-.02em'}}>Every section of the dashboard</h2>
            <p style={{color:T.s4,fontSize:12.5,margin:0}}>Click any section after logging in — here's exactly what you'll find.</p>
          </div>

          <div className="row g-3">
            {/* COL 1 — left features */}
            <div className="col-12 col-lg-6">
              <div style={{display:'flex',flexDirection:'column',gap:10}}>
                {PLATFORM_COLS.left.map(f => (
                  <div key={f.title} className="gc" style={{background:T.card,border:`1px solid ${T.border}`,borderRadius:14,padding:'16px 18px'}}>
                    <div style={{display:'flex',alignItems:'center',gap:10,marginBottom:10}}>
                      <div style={{width:32,height:32,borderRadius:9,background:`${f.color}14`,border:`1px solid ${f.color}20`,display:'flex',alignItems:'center',justifyContent:'center',flexShrink:0}}>
                        <f.icon style={{color:f.color,fontSize:15}}/>
                      </div>
                      <span style={{fontWeight:700,fontSize:13,color:'#fff'}}>{f.title}</span>
                    </div>
                    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'4px 12px'}}>
                      {f.pts.map(pt => (
                        <span key={pt} style={{display:'flex',alignItems:'flex-start',gap:5,fontSize:11,color:T.s4,lineHeight:1.45}}>
                          <RiCheckLine style={{color:f.color,fontSize:10,marginTop:2,flexShrink:0}}/>{pt}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* COL 2 — right features */}
            <div className="col-12 col-lg-6">
              <div style={{display:'flex',flexDirection:'column',gap:10}}>
                {PLATFORM_COLS.right.map(f => (
                  <div key={f.title} className="gc" style={{background:T.card,border:`1px solid ${T.border}`,borderRadius:14,padding:'16px 18px'}}>
                    <div style={{display:'flex',alignItems:'center',gap:10,marginBottom:10}}>
                      <div style={{width:32,height:32,borderRadius:9,background:`${f.color}14`,border:`1px solid ${f.color}20`,display:'flex',alignItems:'center',justifyContent:'center',flexShrink:0}}>
                        <f.icon style={{color:f.color,fontSize:15}}/>
                      </div>
                      <span style={{fontWeight:700,fontSize:13,color:'#fff'}}>{f.title}</span>
                    </div>
                    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'4px 12px'}}>
                      {f.pts.map(pt => (
                        <span key={pt} style={{display:'flex',alignItems:'flex-start',gap:5,fontSize:11,color:T.s4,lineHeight:1.45}}>
                          <RiCheckLine style={{color:f.color,fontSize:10,marginTop:2,flexShrink:0}}/>{pt}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Try free strip */}
          {!isAuth && (
            <div style={{marginTop:20,padding:'14px 20px',background:'rgba(16,185,129,.06)',border:`1px solid ${T.border}`,borderRadius:12,display:'flex',alignItems:'center',justifyContent:'space-between',flexWrap:'wrap',gap:10}}>
              <p style={{fontSize:13,fontWeight:600,color:T.s3,margin:0}}>
                All features free — no card required
              </p>
              <Link to="/register" className="gb"
                style={{display:'inline-flex',alignItems:'center',gap:5,padding:'8px 18px',background:T.grad,color:'#fff',fontWeight:700,borderRadius:9,fontSize:12,boxShadow:'0 3px 10px rgba(16,185,129,.22)'}}>
                Try Free <RiArrowRightLine/>
              </Link>
            </div>
          )}
        </div>
      </section>

      {/* ══ TRENDING ══ */}
      <section style={{background:T.bg2,padding:'48px 20px',borderTop:`1px solid ${T.border}`}}>
        <div className="container-xl">
          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20,flexWrap:'wrap',gap:8}}>
            <div>
              <SLabel icon={RiTrophyLine} color={T.amber} text="Community Picks"/>
              <h2 style={{fontWeight:900,fontSize:'clamp(16px,2vw,22px)',color:'#fff',margin:0,letterSpacing:'-.02em'}}>Trending Markets</h2>
            </div>
            <Link to="/dashboard" style={{fontSize:12,color:T.em,fontWeight:700,display:'flex',alignItems:'center',gap:3}}>View all <RiArrowRightLine/></Link>
          </div>
          <div className="row g-3">
            {TRENDING.map(stock => (
              <div key={stock.sym} className="col-12 col-sm-6 col-lg-4">
                <StockCard stock={stock}/>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ══ ANOMALY ALERTS ══ */}
      <section style={{background:T.bg,padding:'48px 20px',borderTop:`1px solid rgba(239,68,68,.08)`}}>
        <div className="container-xl">
          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20,flexWrap:'wrap',gap:8}}>
            <div>
              <SLabel icon={RiRadarLine} color={T.red} text="Live Detection"/>
              <h2 style={{fontWeight:900,fontSize:'clamp(16px,2vw,22px)',color:'#fff',margin:0,letterSpacing:'-.02em'}}>Anomaly Alerts</h2>
            </div>
            <div style={{display:'flex',alignItems:'center',gap:5,padding:'4px 10px',borderRadius:7,background:'rgba(239,68,68,.08)',border:'1px solid rgba(239,68,68,.15)'}}>
              <span className="blink" style={{width:5,height:5,borderRadius:'50%',background:T.red,display:'inline-block'}}/>
              <span style={{fontSize:9,fontFamily:'DM Mono,monospace',color:T.red,fontWeight:700}}>MONITORING ACTIVE</span>
            </div>
          </div>
          <div className="row g-2">
            {ALERTS.map((a,i) => {
              const sc = sevColor[a.sev]||T.em, tc = typeColor[a.type]||T.em
              return (
                <div key={i} className="col-12 col-lg-6">
                  <div className="gc" style={{background:T.card,border:`1px solid rgba(239,68,68,.09)`,borderRadius:12,padding:'11px 13px',display:'flex',gap:10,position:'relative',overflow:'hidden'}}>
                    <div style={{position:'absolute',left:0,top:0,bottom:0,width:3,background:sc,opacity:.6,borderRadius:'12px 0 0 12px'}}/>
                    <div style={{width:30,height:30,borderRadius:8,background:`${sc}14`,border:`1px solid ${sc}22`,display:'flex',alignItems:'center',justifyContent:'center',flexShrink:0}}>
                      <RiAlertLine style={{color:sc,fontSize:13}}/>
                    </div>
                    <div style={{flex:1,minWidth:0}}>
                      <div style={{display:'flex',alignItems:'center',gap:5,marginBottom:2,flexWrap:'wrap'}}>
                        <span style={{fontWeight:800,fontSize:12,color:'#fff'}}>{a.sym}</span>
                        <span style={{fontSize:8,fontFamily:'DM Mono,monospace',padding:'1px 5px',borderRadius:20,background:`${tc}12`,color:tc,border:`1px solid ${tc}20`,fontWeight:700}}>{a.type}</span>
                        <span style={{marginLeft:'auto',fontSize:9,color:T.s6,fontFamily:'DM Mono,monospace'}}>{a.time}</span>
                      </div>
                      <p style={{fontSize:11,color:T.s4,margin:'0 0 2px',lineHeight:1.4}}>{a.msg}</p>
                      <span style={{fontSize:9,color:sc,fontWeight:700,fontFamily:'DM Mono,monospace'}}>SEVERITY: {a.sev}</span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer style={{borderTop:`1px solid ${T.border}`,padding:'18px 20px',background:T.bg2}}>
        <div style={{maxWidth:1200,margin:'0 auto',display:'flex',alignItems:'center',justifyContent:'space-between',flexWrap:'wrap',gap:10}}>
          <div style={{display:'flex',alignItems:'center',gap:6}}>
            <div style={{width:24,height:24,borderRadius:7,background:T.grad,display:'flex',alignItems:'center',justifyContent:'center'}}>
              <RiLineChartLine style={{color:'#fff',fontSize:11}}/>
            </div>
            <span style={{fontWeight:800,fontSize:13,color:'#fff'}}>Market<span style={{color:T.em}}>AI</span></span>
          </div>
          <div style={{display:'flex',gap:12,fontSize:11,color:T.s6,flexWrap:'wrap'}}>
            <span>NSE · BSE · Binance</span>
            <span>AI by Gemini</span>
            <span>Not financial advice</span>
          </div>
          <p style={{fontSize:10,color:T.s7,fontFamily:'DM Mono,monospace',margin:0}}>© 2026 MarketAI</p>
        </div>
      </footer>
    </div>
  )
}