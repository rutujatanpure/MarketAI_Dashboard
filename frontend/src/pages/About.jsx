import { Link } from 'react-router-dom'
import Navbar from '../components/Navbar'
import { RiBarChartLine, RiArrowRightLine } from 'react-icons/ri'

export default function About() {
  return (
    <div className='min-h-screen pt-16'>
      <Navbar />
      <div className='max-w-4xl mx-auto px-4 py-16'>
        <div className='flex items-center gap-3 mb-6'>
          <RiBarChartLine className='text-cyan-400 text-3xl' />
          <h1 className='font-display font-bold text-3xl text-slate-100'>About</h1>
        </div>
        <p className='text-slate-400 text-lg leading-relaxed mb-8'>
          Market Dashboard is a real-time financial analytics platform tracking
          50+ symbols across NSE, BSE, and global crypto markets with AI-powered
          signals, technical indicators, and risk analysis.
        </p>
        <Link to='/dashboard'
          className='inline-flex items-center gap-2 px-6 py-3 bg-cyan-500 text-slate-950
                     font-semibold rounded-xl hover:bg-cyan-400 transition-colors'>
          Go to Dashboard <RiArrowRightLine />
        </Link>
      </div>
    </div>
  )
}