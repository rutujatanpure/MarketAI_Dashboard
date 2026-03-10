import { usePrices } from '../context/PriceContext'

const RATES  = { USD: 1, INR: 83.5, EUR: 0.92, GBP: 0.79 }
const SYMBOLS = { USD: '$', INR: '₹', EUR: '€', GBP: '£' }

/**
 * const { currency, changeCurrency, format, symbol, rate } = useCurrency()
 * format(1234.56, 2)  →  "₹1,03,010.68"  (when INR selected)
 */
export function useCurrency() {
  const { currency, changeCurrency } = usePrices()

  const rate   = RATES[currency]  ?? 1
  const symbol = SYMBOLS[currency] ?? '$'

  const format = (usdValue, decimals = 2) => {
    if (usdValue == null || isNaN(usdValue)) return `${symbol}—`
    const converted = usdValue * rate
    if (Math.abs(converted) >= 1_000_000)
      return `${symbol}${(converted / 1_000_000).toFixed(2)}M`
    if (Math.abs(converted) >= 1_000)
      return `${symbol}${converted.toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}`
    return `${symbol}${converted.toFixed(decimals)}`
  }

  return { currency, changeCurrency, format, symbol, rate }
}