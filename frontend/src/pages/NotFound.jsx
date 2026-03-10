import { Link } from 'react-router-dom'
import { RiBarChartLine } from 'react-icons/ri'

export default function NotFound() {
  return (
    <div className='min-h-screen flex flex-col items-center justify-center text-center px-4'>
      <RiBarChartLine className='text-cyan-400 text-5xl mb-4 opacity-50' />
      <h1 className='font-display font-bold text-6xl text-slate-700 mb-2'>404</h1>
      <p className='text-slate-500 mb-8'>Page not found</p>
      <Link to='/'
        className='px-6 py-3 bg-cyan-500 text-slate-950 font-semibold rounded-xl
                   hover:bg-cyan-400 transition-colors'>
        Back to Home
      </Link>
    </div>
  )
}