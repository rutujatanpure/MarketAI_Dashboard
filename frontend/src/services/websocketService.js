import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WS_URL = import.meta.env.VITE_WS_URL
let stompClient = null

export const wsService = {
  connect(token, { onConnect, onDisconnect, onCryptoPrice, onStockPrice, onAlert }) {
    stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        onConnect?.()

        // Subscribe to all crypto ticks
        stompClient.subscribe('/topic/prices', msg => {
          try { onCryptoPrice?.(JSON.parse(msg.body)) } catch (_) {}
        })

        // Subscribe to all stock ticks
        stompClient.subscribe('/topic/stocks', msg => {
          try { onStockPrice?.(JSON.parse(msg.body)) } catch (_) {}
        })

        // Subscribe to alerts
        stompClient.subscribe('/topic/alerts', msg => {
          try { onAlert?.(JSON.parse(msg.body)) } catch (_) {}
        })
      },

      onDisconnect: () => onDisconnect?.(),
      onStompError: (frame) => console.error('STOMP error:', frame)
    })

    stompClient.activate()
    return stompClient
  },

  subscribeSymbol(symbol) {
    stompClient?.publish({
      destination: '/app/subscribe',
      body: JSON.stringify({ symbol })
    })
  },

  requestAiAnalysis(symbol) {
    stompClient?.publish({
      destination: '/app/analyze',
      body: JSON.stringify({ symbol })
    })
  },

  subscribeTopic(topic, callback) {
    return stompClient?.subscribe(topic, msg => {
      try { callback(JSON.parse(msg.body)) } catch (_) {}
    })
  },

  disconnect() {
    stompClient?.deactivate()
    stompClient = null
  },

  isConnected() {
    return stompClient?.connected ?? false
  }
}
