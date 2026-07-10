/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        slateDark: '#12141C',
        slateSurface: '#1E2130',
        slateSurfaceLight: '#2A2E45',
        safeGreen: '#4CAF50',
        accentAmber: '#FFB300',
        emergencyRed: '#E53935',
        emergencyRedDark: '#B71C1C',
        textWhite: '#F5F5F7',
        textGray: '#9E9E9E',
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'ping-slow': 'ping 2s cubic-bezier(0, 0, 0.2, 1) infinite',
      }
    },
  },
  plugins: [],
}
