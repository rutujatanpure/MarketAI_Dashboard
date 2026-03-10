/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ["'DM Sans'", "sans-serif"],
      },
      colors: {
        navy: {
          50:  "#f2f6ff",
          100: "#d9e4ff",
          200: "#b3c8ff",
          300: "#809eff",
          400: "#4d73ff",
          500: "#1a47ff",
          600: "#0033cc",
          700: "#002699",
          800: "#001a66",
          900: "#001033",
          950: "#000814",
        },
        emerald: {
          950: "#022c22",
        },
      },
    },
  },
  plugins: [],
}