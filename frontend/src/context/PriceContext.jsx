import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuth } from './AuthContext'
import { apiService } from '../services/apiService'

const PriceContext = createContext(null)

const WS_URL = 'https://marketai-dashboard-3.onrender.com/ws'
export function PriceProvider({ children }) {
  const { getToken, isAuth }     = useAuth()
  const [cryptoPrices, setCryptoPrices] = useState({})   // { BTCUSDT: { price, priceChange, high24h, low24h, volume, timestamp } }
  const [stockPrices,  setStockPrices]  = useState({})   // { AAPL: { price, changePercent, high, low, volume, timestamp } }
  const [alerts,       setAlerts]       = useState([])    // anomaly alerts
  const [connected,    setConnected]    = useState(false)
  const [currency,     setCurrency]     = useState(() => localStorage.getItem('cs_currency') || 'USD')
  const clientRef = useRef(null)

  // Initial REST fetch to hydrate state before WS connects
  useEffect(() => {
    if (!isAuth) return
    apiService.get('/api/crypto/all').then(r => {
      const map = {}
      ;(r.data || []).forEach(p => { map[p.symbol] = p })
      setCryptoPrices(map)
    }).catch(() => {})

    apiService.get('/api/market/stocks').then(r => {
      const map = {}
      ;(r.data || []).forEach(p => { map[p.symbol] = p })
      setStockPrices(map)
    }).catch(() => {})
  }, [isAuth])

  // WebSocket connection
  useEffect(() => {
    if (!isAuth) return

    const token  = getToken()
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders:   token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay:   5000,
      onConnect: () => {
        setConnected(true)

        client.subscribe('/topic/prices', msg => {
          try {
            const evt = JSON.parse(msg.body)
            setCryptoPrices(prev => ({ ...prev, [evt.symbol]: evt }))
          } catch {}
        })

        client.subscribe('/topic/stocks', msg => {
          try {
            const evt = JSON.parse(msg.body)
            setStockPrices(prev => ({ ...prev, [evt.symbol]: evt }))
          } catch {}
        })

        client.subscribe('/topic/alerts', msg => {
          try {
            const alert = JSON.parse(msg.body)
            setAlerts(prev => [alert, ...prev].slice(0, 50))
          } catch {}
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      setConnected(false)
    }
  }, [isAuth, getToken])

  const subscribeSymbol = useCallback((symbol) => {
    clientRef.current?.publish({ destination: '/app/subscribe', body: JSON.stringify({ symbol }) })
  }, [])

  const requestAnalysis = useCallback((symbol, callback) => {
    if (!clientRef.current?.connected) return null
    clientRef.current.publish({ destination: '/app/analyze', body: JSON.stringify({ symbol }) })
    return clientRef.current.subscribe(`/topic/analysis/${symbol}`, msg => {
      try { callback(JSON.parse(msg.body)) } catch {}
    })
  }, [])

  const changeCurrency = useCallback((c) => {
    setCurrency(c)
    localStorage.setItem('cs_currency', c)
  }, [])

  return (
    <PriceContext.Provider value={{
      cryptoPrices, stockPrices, alerts,
      connected, subscribeSymbol, requestAnalysis,
      currency, changeCurrency,
    }}>
      {children}
    </PriceContext.Provider>
  )
}

export function usePrices() {
  const ctx = useContext(PriceContext)
  if (!ctx) throw new Error('usePrices must be used inside PriceProvider')
  return ctx
}
