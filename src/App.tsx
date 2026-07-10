import { useState, useEffect, useRef } from 'react';
import { 
  ShieldAlert, 
  Phone, 
  MessageSquare, 
  MapPin, 
  User, 
  Settings, 
  Volume2, 
  VolumeX, 
  RotateCcw, 
  Send, 
  Heart, 
  Activity, 
  Info, 
  ChevronRight, 
  Star, 
  CheckCircle, 
  AlertTriangle, 
  RefreshCw, 
  Mail, 
  Key, 
  PhoneCall,
  Menu,
  FileText
} from 'lucide-react';
import { GeminiManager, CoachResponse } from './GeminiManager';

// Screens Enum
type ScreenType = 'SPLASH' | 'ONBOARDING' | 'MAIN';

// Navigation Tabs Enum
type TabType = 'HOME' | 'BOOKINGS' | 'AI_COACH' | 'PROFILE';

// Profile Sections Enum
type ProfileSectionType = 'MAIN' | 'VERIFY_EMAIL' | 'CHANGE_PASSWORD';

// Log item interface
interface RescueLog {
  id: string;
  timestamp: string;
  sender: string; // "Bystander", "Vocal Coach", "Vocal Coach (Offline Core)"
  message: string;
  actionCode?: string;
  isWarning: boolean;
}

export default function App() {
  // Theme & App State
  const [isDarkTheme, setIsDarkTheme] = useState(true);
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('SPLASH');
  const [onboardingStep, setOnboardingStep] = useState(0);
  const [showSignIn, setShowSignIn] = useState(false);
  const [isFirstAiderSelected, setIsFirstAiderSelected] = useState(true);

  // Authenticated User State (Matches Android attributes)
  const [userName, setUserName] = useState('');
  const [userPhone, setUserPhone] = useState('');
  const [userEmail, setUserEmail] = useState('');
  const [userPassword, setUserPassword] = useState('');
  const [userRole, setUserRole] = useState('Guest Bystander');
  const [certId, setCertId] = useState('');
  const [userApiKey, setUserApiKey] = useState('');

  // Main navigation active tab
  const [activeTab, setActiveTab] = useState<TabType>('HOME');

  // Coach Scenario & Status state
  const [scenario, setScenario] = useState('Road Traffic Crash');
  const [victimStatus, setVictimStatus] = useState('Unconscious on ground');
  const [bystanderInput, setBystanderInput] = useState('');
  const [sessionLogs, setSessionLogs] = useState<RescueLog[]>([]);

  // Vocal Coach instruction state
  const [currentInstruction, setCurrentInstruction] = useState(
    "Ensure you are safe from oncoming traffic before approaching the victim. Place warning symbols or triangles if available."
  );
  const [nextRecommendedAction, setNextRecommendedAction] = useState('SCENE_SAFETY');
  const [isWarningTriggered, setIsWarningTriggered] = useState(true);

  // Audio/TTS state
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [isMuted, setIsMuted] = useState(false);

  // Simulated GPS Responder Tracking State
  const [responderDistance, setResponderDistance] = useState(1.8); // in km
  const [responderMinutes, setResponderMinutes] = useState(6); // in mins
  const [responderStatus, setResponderStatus] = useState('First-aider booked & navigating');
  const [responderName, setResponderName] = useState('Emeka Okafor (TouchLife Responder ID: 2901)');

  // UI state for Loading or Error
  const [isCoachLoading, setIsCoachLoading] = useState(false);
  const [coachError, setCoachError] = useState<string | null>(null);

  // UI toast simulation
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Ref for chat auto scroll
  const chatLogsEndRef = useRef<HTMLDivElement>(null);

  // Ref for canvas element
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // Profile subsections
  const [profileSection, setProfileSection] = useState<ProfileSectionType>('MAIN');

  // Trigger temporary toasts
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage(null);
    }, 3000);
  };

  // Browser-native TTS speaker
  const speakInstruction = (text: string) => {
    if (isMuted) return;
    try {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        const voices = window.speechSynthesis.getVoices();
        // Prefer English/Premium voice
        const enVoice = voices.find(v => v.lang.startsWith('en')) || voices[0];
        if (enVoice) utterance.voice = enVoice;
        
        utterance.onstart = () => setIsSpeaking(true);
        utterance.onend = () => setIsSpeaking(false);
        utterance.onerror = () => setIsSpeaking(false);
        
        window.speechSynthesis.speak(utterance);
      }
    } catch (e) {
      console.error("Speech Synthesis error:", e);
    }
  };

  // Stop TTS
  const stopInstruction = () => {
    try {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
        setIsSpeaking(false);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 1. Splash Screen Sequence (3.5 seconds)
  useEffect(() => {
    if (currentScreen === 'SPLASH') {
      const timer = setTimeout(() => {
        setCurrentScreen('ONBOARDING');
      }, 3500);
      return () => clearTimeout(timer);
    }
  }, [currentScreen]);

  // Initial Safety instruction vocalized upon starting Main tab
  useEffect(() => {
    if (currentScreen === 'MAIN' && sessionLogs.length === 0) {
      // Create initial log
      const initialLog: RescueLog = {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        sender: 'Vocal Coach',
        message: currentInstruction,
        actionCode: nextRecommendedAction,
        isWarning: true
      };
      setSessionLogs([initialLog]);
      
      // Auto vocalize initial step
      const speechTimer = setTimeout(() => {
        speakInstruction(currentInstruction);
      }, 1500);
      return () => clearTimeout(speechTimer);
    }
  }, [currentScreen]);

  // Simulated GPS Responder Tracking Simulation Loop
  useEffect(() => {
    if (currentScreen === 'MAIN') {
      const interval = setInterval(() => {
        setResponderDistance(prev => {
          if (prev > 0.1) {
            const nextVal = parseFloat((prev - 0.15).toFixed(2));
            if (nextVal <= 0.1) {
              setResponderMinutes(0);
              setResponderStatus('First-aider arrived at scene!');
              return 0.0;
            }
            // Close the minute ETA dynamically
            setResponderMinutes(prevMin => {
              if (prevMin > 1 && nextVal < prevMin * 0.3) {
                return prevMin - 1;
              }
              return prevMin;
            });
            return nextVal;
          } else {
            setResponderMinutes(0);
            setResponderStatus('First-aider arrived at scene!');
            return 0.0;
          }
        });
      }, 12000); // simulation tick every 12 seconds
      return () => clearInterval(interval);
    }
  }, [currentScreen]);

  // Scroll to bottom of rescue logs
  useEffect(() => {
    chatLogsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [sessionLogs]);

  // Redraw map on distance changes or map toggles
  const [isMapSatellite, setIsMapSatellite] = useState(false);
  useEffect(() => {
    if (activeTab === 'BOOKINGS' && canvasRef.current) {
      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      const w = canvas.width;
      const h = canvas.height;

      // Base map background
      ctx.fillStyle = isMapSatellite ? '#13141F' : '#1E2130';
      ctx.fillRect(0, 0, w, h);

      // Draw Grid Streets
      ctx.lineWidth = 8;
      ctx.strokeStyle = isMapSatellite ? '#232635' : '#2E3B4E';

      // Horizontal
      ctx.beginPath();
      ctx.moveTo(0, h * 0.2); ctx.lineTo(w, h * 0.2);
      ctx.moveTo(0, h * 0.5); ctx.lineTo(w, h * 0.5);
      ctx.moveTo(0, h * 0.8); ctx.lineTo(w, h * 0.8);
      // Vertical
      ctx.moveTo(w * 0.25, 0); ctx.lineTo(w * 0.25, h);
      ctx.moveTo(w * 0.65, 0); ctx.lineTo(w * 0.65, h);
      // Diagonal street block
      ctx.moveTo(0, 0); ctx.lineTo(w, h);
      ctx.stroke();

      // Landmarks
      ctx.fillStyle = '#1B3D2A'; // Park Green
      ctx.fillRect(w * 0.05, h * 0.28, w * 0.15, h * 0.12);
      ctx.fillStyle = '#352B1E'; // Building Brown
      ctx.fillRect(w * 0.75, h * 0.28, w * 0.15, h * 0.12);

      // User/Victim Position (fixed at bottom-right corner)
      const userX = w * 0.65;
      const userY = h * 0.8;

      // Pulse ring user location
      ctx.beginPath();
      ctx.arc(userX, userY, 24, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(30, 136, 229, 0.2)';
      ctx.fill();

      ctx.beginPath();
      ctx.arc(userX, userY, 8, 0, Math.PI * 2);
      ctx.fillStyle = '#1E88E5';
      ctx.fill();

      // Responder moves on diagonal from top-left block
      const startX = w * 0.25;
      const startY = h * 0.2;
      const progress = Math.max(0, Math.min(1, (1.8 - responderDistance) / 1.8));
      const respX = startX + (userX - startX) * progress;
      const respY = startY + (userY - startY) * progress;

      // Line path from responder to user
      ctx.lineWidth = 3;
      ctx.strokeStyle = '#FFB300'; // Traveled path
      ctx.beginPath();
      ctx.moveTo(startX, startY);
      ctx.lineTo(respX, respY);
      ctx.stroke();

      ctx.lineWidth = 4;
      ctx.strokeStyle = '#4CAF50'; // Remaining path
      ctx.beginPath();
      ctx.moveTo(respX, respY);
      ctx.lineTo(userX, userY);
      ctx.stroke();

      // Responder position dot
      if (responderDistance > 0) {
        ctx.beginPath();
        ctx.arc(respX, respY, 24, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(76, 175, 80, 0.2)';
        ctx.fill();

        ctx.beginPath();
        ctx.arc(respX, respY, 8, 0, Math.PI * 2);
        ctx.fillStyle = '#4CAF50';
        ctx.fill();
      }
    }
  }, [activeTab, responderDistance, isMapSatellite]);

  // Handle registration submission
  const handleRegister = (e: React.FormEvent) => {
    e.preventDefault();
    if (!userName.trim() || !userPhone.trim()) {
      showToast("Please fill in Name and Phone Number");
      return;
    }
    setUserRole(isFirstAiderSelected ? "Certified First-Aider" : "Citizen Bystander");
    setCurrentScreen('MAIN');
    showToast("Successfully registered account!");
  };

  // Skip auth flow
  const handleSkipAuth = () => {
    setUserName('Guest Bystander');
    setUserRole('Guest Bystander');
    setCurrentScreen('MAIN');
    showToast("Continuing as Guest Bystander");
  };

  // Submit emergency updates
  const submitBystanderUpdate = async (customInput?: string) => {
    const inputToUse = customInput || bystanderInput;
    if (!inputToUse.trim()) return;

    // Log the bystander input
    const newBystanderLog: RescueLog = {
      id: Math.random().toString(),
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      sender: 'Bystander',
      message: inputToUse,
      isWarning: false
    };

    setSessionLogs(prev => [...prev, newBystanderLog]);
    setBystanderInput('');
    setIsCoachLoading(true);
    setCoachError(null);

    try {
      const response = await GeminiManager.getFirstAidInstruction(
        scenario,
        victimStatus,
        inputToUse,
        userApiKey
      );

      // Update flow values
      setCurrentInstruction(response.voiceResponse);
      setNextRecommendedAction(response.nextRecommendedAction);
      setIsWarningTriggered(response.safetyWarningTriggered);

      // Create AI instruction log
      const newAiLog: RescueLog = {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        sender: response.isOfflineFallback ? "Vocal Coach (Offline Core)" : "Vocal Coach",
        message: response.voiceResponse,
        actionCode: response.nextRecommendedAction,
        isWarning: response.safetyWarningTriggered
      };

      setSessionLogs(prev => [...prev, newAiLog]);
      speakInstruction(response.voiceResponse);

      // Update victim status helper based on next action recommended
      switch (response.nextRecommendedAction) {
        case "BLEEDING_CONTROL":
          setVictimStatus("Bleeding heavily, pressure being applied");
          break;
        case "CPR_CHECK":
          setVictimStatus("Unconscious, CPR in progress");
          break;
        case "SPINAL_ALERT":
          setVictimStatus("High-impact trauma, head stabilized");
          break;
        case "CHOKING_RESCUE":
          setVictimStatus("Choking, abdominal thrusts active");
          break;
        case "COMPLETE":
          setVictimStatus("Stable, waiting for responder arrival");
          break;
      }
    } catch (e: any) {
      console.error(e);
      setCoachError(e.message || "Failed to contact vocal coach");
      
      // Automatic fallback trigger
      const fallback = GeminiManager.getOfflineInstruction(scenario, victimStatus, inputToUse);
      setCurrentInstruction(fallback.voiceResponse);
      setNextRecommendedAction(fallback.nextRecommendedAction);
      setIsWarningTriggered(fallback.safetyWarningTriggered);

      const fallbackLog: RescueLog = {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        sender: "Vocal Coach (Local Safety core)",
        message: fallback.voiceResponse,
        actionCode: fallback.nextRecommendedAction,
        isWarning: fallback.safetyWarningTriggered
      };

      setSessionLogs(prev => [...prev, fallbackLog]);
      speakInstruction(fallback.voiceResponse);
    } finally {
      setIsCoachLoading(false);
    }
  };

  // Reset core states to defaults
  const resetSession = () => {
    setScenario("Road Traffic Crash");
    setVictimStatus("Unconscious on ground");
    setBystanderInput("");
    const initialMsg = "Ensure you are safe from oncoming traffic before approaching the victim. Place warning symbols or triangles if available.";
    setCurrentInstruction(initialMsg);
    setNextRecommendedAction("SCENE_SAFETY");
    setIsWarningTriggered(true);
    setIsCoachLoading(false);
    setCoachError(null);

    const initialLog: RescueLog = {
      id: Math.random().toString(),
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      sender: "Vocal Coach",
      message: initialMsg,
      actionCode: "SCENE_SAFETY",
      isWarning: true
    };
    setSessionLogs([initialLog]);

    // Reset Bookings
    setResponderDistance(1.8);
    setResponderMinutes(6);
    setResponderStatus("First-aider booked & navigating");

    speakInstruction(initialMsg);
    showToast("Session logs and status reset.");
  };

  // Replay instruction
  const replayInstruction = () => {
    speakInstruction(currentInstruction);
    showToast("Replaying instruction voice...");
  };

  // Toggle Mute
  const toggleMute = () => {
    if (!isMuted) {
      stopInstruction();
      setIsMuted(true);
      showToast("Vocal Coach muted");
    } else {
      setIsMuted(false);
      showToast("Vocal Coach unmuted");
      speakInstruction(currentInstruction);
    }
  };

  // Mock preset buttons to quick-submit
  const triggerQuickUpdate = (label: string) => {
    setBystanderInput(label);
    submitBystanderUpdate(label);
  };

  return (
    <div className={`min-h-screen flex flex-col justify-between font-sans transition-all duration-300 ${isDarkTheme ? 'bg-[#12141C] text-[#F5F5F7]' : 'bg-gray-100 text-gray-900'}`}>
      
      {/* Toast Notification HUD */}
      {toastMessage && (
        <div className="fixed top-6 left-1/2 -translate-x-1/2 z-50 bg-[#1E2130] border border-[#4CAF50] text-[#4CAF50] font-bold py-3 px-6 rounded-xl shadow-2xl flex items-center gap-3 animate-bounce">
          <CheckCircle size={18} />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ==================== 1. SPLASH SCREEN SCREEN ==================== */}
      {currentScreen === 'SPLASH' && (
        <div className="flex-1 flex flex-col items-center justify-center relative p-6 select-none bg-[#12141C]">
          {/* Radial Glowing Ambient Backdrop */}
          <div className="absolute w-[350px] h-[350px] rounded-full bg-[#E53935]/10 blur-[100px]" />
          
          <div className="flex flex-col items-center z-10 space-y-8">
            {/* Animated Pulsating Logo */}
            <div className="w-40 h-40 rounded-full border-2 border-safeGreen shadow-[0_0_25px_rgba(76,175,80,0.4)] overflow-hidden flex items-center justify-center bg-slateSurface animate-pulse">
              <img 
                src="/img_touchlife_logo.jpg" 
                alt="TouchLife Logo" 
                className="w-full h-full object-cover rounded-full"
                onError={(e) => {
                  // Fallback if image not ready/present
                  e.currentTarget.src = "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=300";
                }}
              />
            </div>

            {/* App Branding Header */}
            <div className="text-center space-y-3 animate-fade-in">
              <h1 className="text-3xl font-extrabold tracking-[0.25em] text-white">TOUCHLIFE</h1>
              <p className="text-[#FFB300] text-xs font-bold tracking-[0.15em] uppercase">Vocal First-Aid Coach</p>
              <div className="h-[2px] w-12 bg-safeGreen mx-auto rounded-full mt-2" />
            </div>

            {/* Nigeria Subtitle */}
            <p className="text-textGray text-xs font-semibold select-none">Nigeria's First Decentralized Emergency Rescue</p>
          </div>
        </div>
      )}

      {/* ==================== 2. ONBOARDING SCREEN ==================== */}
      {currentScreen === 'ONBOARDING' && (
        <div className="flex-1 flex flex-col p-6 max-w-md mx-auto w-full justify-between relative select-none bg-[#12141C]">
          {/* Glowing Ambient Shapes */}
          <div className="absolute top-0 right-0 w-[260px] h-[260px] rounded-full bg-[#4CAF50]/5 blur-[80px]" />
          <div className="absolute bottom-0 left-0 w-[260px] h-[260px] rounded-full bg-[#E53935]/5 blur-[80px]" />

          {/* Header row */}
          <div className="flex items-center justify-between py-3 z-10">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-full border border-safeGreen overflow-hidden flex items-center justify-center bg-slateSurface">
                <img src="/img_touchlife_logo.jpg" alt="Mini Logo" className="w-full h-full object-cover" />
              </div>
              <span className="text-sm font-black tracking-widest text-white">TOUCHLIFE</span>
            </div>
            
            {!showSignIn && (
              <button 
                onClick={() => setShowSignIn(true)}
                className="bg-slateSurface hover:bg-slateSurfaceLight text-xs text-textGray font-bold py-2 px-4 rounded-xl border border-gray-800 transition-all duration-200"
              >
                Skip ➔
              </button>
            )}
          </div>

          {/* Interactive Slides Stage */}
          {!showSignIn ? (
            <div className="flex-1 flex flex-col justify-between py-8">
              {/* Animation Card */}
              <div className="flex-1 flex items-center justify-center p-6 bg-slateSurface rounded-3xl border border-gray-800 shadow-xl min-h-[220px]">
                {onboardingStep === 0 && (
                  <div className="text-center space-y-4">
                    <div className="w-24 h-24 bg-emergencyRed/10 rounded-full flex items-center justify-center mx-auto text-emergencyRed border border-emergencyRed/20 animate-pulse">
                      <ShieldAlert size={48} />
                    </div>
                    <span className="text-xs text-emergencyRed font-bold bg-emergencyRed/10 px-3 py-1 rounded-full border border-emergencyRed/20">LIVE LOCATION SYNC</span>
                  </div>
                )}
                {onboardingStep === 1 && (
                  <div className="text-center space-y-4">
                    <div className="w-24 h-24 bg-[#FFB300]/10 rounded-full flex items-center justify-center mx-auto text-[#FFB300] border border-[#FFB300]/20 animate-bounce">
                      <Volume2 size={48} />
                    </div>
                    <span className="text-xs text-[#FFB300] font-bold bg-[#FFB300]/10 px-3 py-1 rounded-full border border-[#FFB300]/20">HANDS-FREE TTS COACH</span>
                  </div>
                )}
                {onboardingStep === 2 && (
                  <div className="text-center space-y-4">
                    <div className="w-24 h-24 bg-safeGreen/10 rounded-full flex items-center justify-center mx-auto text-safeGreen border border-safeGreen/20 animate-pulse">
                      <Activity size={48} />
                    </div>
                    <span className="text-xs text-safeGreen font-bold bg-safeGreen/10 px-3 py-1 rounded-full border border-safeGreen/20">DECENTRALIZED SURVIVAL LOOP</span>
                  </div>
                )}
              </div>

              {/* Descriptions block */}
              <div className="text-center py-6 space-y-3">
                <h2 className="text-xl font-black text-accentAmber tracking-wider uppercase">
                  {onboardingStep === 0 ? "RAPID SCENE DISPATCH" : onboardingStep === 1 ? "VOCAL HANDS-FREE COACH" : "COOPERATIVE NETWORK"}
                </h2>
                <p className="text-sm text-textWhite/80 leading-relaxed px-4">
                  {onboardingStep === 0 
                    ? "Routes the closest certified responder to motorcycle (Okada) or road crash coordinates immediately." 
                    : onboardingStep === 1 
                      ? "Step-by-step interactive audio instructions guide you in life-saving first-aid triage." 
                      : "Uniting trained volunteers, bystanders, and operations into a decentralized survival loop to save lives."}
                </p>
              </div>

              {/* Dots Controls */}
              <div className="flex items-center justify-center gap-3 py-4">
                {[0, 1, 2].map(step => (
                  <button 
                    key={step} 
                    onClick={() => setOnboardingStep(step)}
                    className={`h-2.5 rounded-full transition-all duration-300 ${step === onboardingStep ? 'w-6 bg-safeGreen' : 'w-2.5 bg-gray-700'}`}
                  />
                ))}
              </div>

              {/* Navigation button */}
              <button
                onClick={() => {
                  if (onboardingStep < 2) {
                    setOnboardingStep(prev => prev + 1);
                  } else {
                    setShowSignIn(true);
                  }
                }}
                className={`w-full py-4 rounded-xl font-bold text-white transition-all duration-300 flex items-center justify-center gap-2 shadow-lg ${onboardingStep === 2 ? 'bg-safeGreen hover:bg-green-600' : 'bg-slateSurface hover:bg-slateSurfaceLight border border-safeGreen'}`}
              >
                <span>{onboardingStep === 2 ? "Get Started ➔" : "Next Feature ➔"}</span>
              </button>
            </div>
          ) : (
            // Form sign-in register stage
            <div className="flex-1 flex flex-col justify-between py-6">
              <div className="space-y-4">
                <div className="text-center pb-2">
                  <h2 className="text-lg font-extrabold text-white">SIGN IN OR REGISTER</h2>
                  <p className="text-xs text-textGray leading-relaxed mt-1">Register as a trained medical volunteer, bystander, or skip to continue as a guest.</p>
                </div>

                {/* Profile type selection toggle tabs */}
                <div className="bg-[#12141C] p-1.5 rounded-2xl flex gap-2 border border-gray-800">
                  <button
                    type="button"
                    onClick={() => setIsFirstAiderSelected(true)}
                    className={`flex-1 py-3 px-4 rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all duration-200 ${isFirstAiderSelected ? 'bg-safeGreen text-white shadow-md' : 'text-textGray hover:text-white'}`}
                  >
                    <Star size={14} />
                    First-Aider
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsFirstAiderSelected(false)}
                    className={`flex-1 py-3 px-4 rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all duration-200 ${!isFirstAiderSelected ? 'bg-emergencyRed text-white shadow-md' : 'text-textGray hover:text-white'}`}
                  >
                    <User size={14} />
                    Citizen/User
                  </button>
                </div>

                <form onSubmit={handleRegister} className="space-y-3.5 pt-2">
                  <div>
                    <label className="text-xs font-bold text-textGray uppercase block mb-1">Full Name</label>
                    <input 
                      type="text" 
                      placeholder={isFirstAiderSelected ? "e.g. Dr. Emeka Obi" : "e.g. Kola Adesina"}
                      value={userName}
                      onChange={(e) => setUserName(e.target.value)}
                      className="w-full bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-xl py-3 px-4 text-sm outline-none transition-all duration-200"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-textGray uppercase block mb-1">Phone Number</label>
                    <input 
                      type="tel" 
                      placeholder="e.g. +234 803 123 4567"
                      value={userPhone}
                      onChange={(e) => setUserPhone(e.target.value)}
                      className="w-full bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-xl py-3 px-4 text-sm outline-none transition-all duration-200"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-textGray uppercase block mb-1">Email Address (Optional)</label>
                    <input 
                      type="email" 
                      placeholder="e.g. name@example.com"
                      value={userEmail}
                      onChange={(e) => setUserEmail(e.target.value)}
                      className="w-full bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-xl py-3 px-4 text-sm outline-none transition-all duration-200"
                    />
                  </div>
                  {isFirstAiderSelected && (
                    <div>
                      <label className="text-xs font-bold text-textGray uppercase block mb-1">First-Aid License Cert ID</label>
                      <input 
                        type="text" 
                        placeholder="e.g. NCRS-2901"
                        value={certId}
                        onChange={(e) => setCertId(e.target.value)}
                        className="w-full bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-xl py-3 px-4 text-sm outline-none transition-all duration-200"
                      />
                    </div>
                  )}

                  <button
                    type="submit"
                    className="w-full py-3.5 bg-safeGreen hover:bg-green-600 font-bold text-white rounded-xl shadow-lg transition-all duration-200"
                  >
                    Submit Registration
                  </button>
                </form>
              </div>

              {/* Guest / Bystander Option */}
              <div className="pt-4 border-t border-gray-800/60 text-center space-y-3">
                <button
                  type="button"
                  onClick={handleSkipAuth}
                  className="text-sm font-semibold text-accentAmber hover:underline block mx-auto"
                >
                  Skip & Continue as Guest Bystander ➔
                </button>
                <button 
                  onClick={() => setShowSignIn(false)}
                  className="text-xs text-textGray hover:text-white"
                >
                  ➔ Back to Info Slides
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ==================== 3. MAIN WORKSPACE ==================== */}
      {currentScreen === 'MAIN' && (
        <div className="flex-1 flex flex-col justify-between max-w-lg mx-auto w-full relative">
          
          {/* Header HUD */}
          <header className={`p-4 border-b ${isDarkTheme ? 'border-gray-800/80 bg-[#12141C]/90' : 'border-gray-200 bg-white'} sticky top-0 z-40 backdrop-blur-md`}>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-full border border-safeGreen overflow-hidden bg-slateSurface flex items-center justify-center">
                  <img src="/img_touchlife_logo.jpg" alt="Logo" className="w-full h-full object-cover" />
                </div>
                <div>
                  <h1 className="text-sm font-black tracking-wider text-white">TOUCHLIFE</h1>
                  <p className="text-[10px] text-safeGreen font-bold tracking-widest uppercase">Decentralized Rescue Active</p>
                </div>
              </div>

              {/* Panic mode label */}
              <div className="bg-emergencyRed/10 border border-emergencyRed/20 py-1.5 px-3 rounded-full flex items-center gap-2 animate-pulse">
                <div className="w-2.5 h-2.5 rounded-full bg-emergencyRed" />
                <span className="text-[10px] font-black text-emergencyRed uppercase tracking-wider">EMERGENCY ACTIVE</span>
              </div>
            </div>
          </header>

          {/* Core Tab Views container */}
          <main className="flex-1 overflow-y-auto p-4 space-y-6 pb-24">
            
            {/* -------------------- HOME TAB -------------------- */}
            {activeTab === 'HOME' && (
              <div className="space-y-6 animate-fade-in">
                
                {/* 1. SCENARIO CONFIGURATION CARDS */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Scenario selection dropdown */}
                  <div className="bg-slateSurface p-4 rounded-2xl border border-gray-800">
                    <label className="text-[10px] font-extrabold text-accentAmber uppercase tracking-wider block mb-2">Emergency Accident Incident</label>
                    <select 
                      value={scenario}
                      onChange={(e) => {
                        setScenario(e.target.value);
                        showToast(`Scenario updated to ${e.target.value}`);
                      }}
                      className="w-full bg-[#12141C] text-white py-2 px-3 rounded-lg text-sm border border-gray-800 outline-none"
                    >
                      <option value="Road Traffic Crash">Road Traffic Crash</option>
                      <option value="Severe Okada Fall">Severe Okada Fall</option>
                      <option value="Bleeding Wound incident">Bleeding Wound Incident</option>
                      <option value="Severe Choking Emergency">Choking Emergency</option>
                      <option value="Cardiac arrest / Unresponsive">Cardiac Arrest / Unresponsive</option>
                    </select>
                  </div>

                  {/* Victim status selection dropdown */}
                  <div className="bg-slateSurface p-4 rounded-2xl border border-gray-800">
                    <label className="text-[10px] font-extrabold text-safeGreen uppercase tracking-wider block mb-2">Victim Medical Status</label>
                    <select 
                      value={victimStatus}
                      onChange={(e) => {
                        setVictimStatus(e.target.value);
                        showToast(`Victim status updated to ${e.target.value}`);
                      }}
                      className="w-full bg-[#12141C] text-white py-2 px-3 rounded-lg text-sm border border-gray-800 outline-none"
                    >
                      <option value="Unconscious on ground">Unconscious on ground</option>
                      <option value="Heavily bleeding, unconscious">Heavily bleeding, unconscious</option>
                      <option value="Bleeding heavily but awake">Bleeding heavily but awake</option>
                      <option value="Choking, unable to breathe">Choking, unable to breathe</option>
                      <option value="No response, not breathing">No response, not breathing</option>
                      <option value="Stable, head stabilized">Stable, head stabilized</option>
                    </select>
                  </div>
                </div>

                {/* 2. THE MAIN VOCAL COACH INSTRUCTION BOARD */}
                <div className="bg-slateSurface border-2 border-safeGreen/30 rounded-3xl p-5 shadow-2xl relative overflow-hidden space-y-4">
                  {/* Decorative glowing pulse in corners */}
                  {isSpeaking && (
                    <div className="absolute top-0 right-0 w-32 h-32 bg-safeGreen/5 blur-xl rounded-full" />
                  )}

                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-accentAmber animate-ping" />
                      <span className="text-[11px] font-black text-accentAmber uppercase tracking-widest">LIVE COCH GUIDANCE</span>
                    </div>

                    <div className="flex items-center gap-2">
                      {/* Replay */}
                      <button 
                        onClick={replayInstruction}
                        className="p-2 rounded-xl bg-[#12141C] border border-gray-800 text-accentAmber hover:bg-[#1E2130] transition-all"
                        title="Replay Voice Guidance"
                      >
                        <RotateCcw size={14} />
                      </button>

                      {/* Mute toggle */}
                      <button 
                        onClick={toggleMute}
                        className={`p-2 rounded-xl bg-[#12141C] border border-gray-800 hover:bg-[#1E2130] transition-all ${isMuted ? 'text-emergencyRed' : 'text-safeGreen'}`}
                        title={isMuted ? "Unmute Voice" : "Mute Voice"}
                      >
                        {isMuted ? <VolumeX size={14} /> : <Volume2 size={14} />}
                      </button>

                      {/* Reset */}
                      <button 
                        onClick={resetSession}
                        className="p-2 rounded-xl bg-[#12141C] border border-gray-800 text-gray-400 hover:text-white hover:bg-[#1E2130] transition-all"
                        title="Restart Session"
                      >
                        <RefreshCw size={14} />
                      </button>
                    </div>
                  </div>

                  {/* Speech Visualizer Ring */}
                  <div className="flex items-center justify-center py-2">
                    <div className="relative flex items-center justify-center">
                      <div className={`absolute w-16 h-16 rounded-full bg-safeGreen/10 transition-all duration-300 ${isSpeaking ? 'animate-ping' : ''}`} />
                      <div className={`absolute w-20 h-20 rounded-full bg-safeGreen/5 transition-all duration-300 ${isSpeaking ? 'animate-ping-slow' : ''}`} />
                      <div className={`w-12 h-12 rounded-full flex items-center justify-center border border-safeGreen/30 z-10 transition-all ${isSpeaking ? 'bg-safeGreen text-white' : 'bg-[#12141C] text-safeGreen'}`}>
                        <Volume2 size={20} className={isSpeaking ? 'animate-bounce' : ''} />
                      </div>
                    </div>
                  </div>

                  {/* Instruction text (Big typography with Material guidelines) */}
                  <div className="text-center px-2">
                    {isWarningTriggered && (
                      <div className="flex items-center justify-center gap-1.5 text-emergencyRed text-[11px] font-black tracking-wider uppercase mb-2">
                        <AlertTriangle size={14} />
                        <span>TRAUMA SAFETY ALERT</span>
                      </div>
                    )}
                    <p className="text-white text-base md:text-lg font-medium leading-relaxed">
                      "{currentInstruction}"
                    </p>
                  </div>

                  {/* Action code badge */}
                  <div className="flex justify-center">
                    <span className="bg-[#12141C] border border-gray-800 text-gray-400 font-bold text-[10px] tracking-widest px-3 py-1.5 rounded-full uppercase">
                      Action Phase: <strong className="text-safeGreen">{nextRecommendedAction}</strong>
                    </span>
                  </div>
                </div>

                {/* 3. BYSTANDER VOICE / TEXT INPUT FORM */}
                <div className="space-y-3">
                  <h3 className="text-xs font-extrabold text-textGray uppercase tracking-widest block px-1">Describe Incident Scene Update</h3>
                  
                  <div className="flex gap-2">
                    <input 
                      type="text"
                      placeholder="Tell coach what is happening..."
                      value={bystanderInput}
                      onChange={(e) => setBystanderInput(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && submitBystanderUpdate()}
                      className="flex-1 bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-2xl py-3 px-4 text-sm outline-none outline-0 shadow-lg transition-all"
                    />
                    <button 
                      onClick={() => submitBystanderUpdate()}
                      disabled={isCoachLoading}
                      className="bg-safeGreen hover:bg-green-600 disabled:bg-gray-700 text-white p-3.5 rounded-2xl shadow-xl transition-all flex items-center justify-center min-w-[50px]"
                    >
                      {isCoachLoading ? (
                        <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                      ) : (
                        <Send size={18} />
                      )}
                    </button>
                  </div>

                  {/* Quick Preset helper Chips for high-stress situations */}
                  <div className="space-y-2">
                    <span className="text-[10px] font-extrabold text-textGray tracking-wider uppercase block">Quick Situation Updates</span>
                    <div className="flex flex-wrap gap-2">
                      <button 
                        onClick={() => triggerQuickUpdate("He is bleeding heavily from his thigh.")}
                        className="bg-slateSurface hover:bg-[#2A2E45] border border-gray-800 rounded-xl px-3 py-1.5 text-xs text-textWhite font-medium transition-all"
                      >
                        🩸 Bleeding heavily
                      </button>
                      <button 
                        onClick={() => triggerQuickUpdate("He is unconscious but breathing.")}
                        className="bg-slateSurface hover:bg-[#2A2E45] border border-gray-800 rounded-xl px-3 py-1.5 text-xs text-textWhite font-medium transition-all"
                      >
                        💤 Unconscious but breathing
                      </button>
                      <button 
                        onClick={() => triggerQuickUpdate("He is not breathing at all.")}
                        className="bg-slateSurface hover:bg-[#2A2E45] border border-gray-800 rounded-xl px-3 py-1.5 text-xs text-textWhite font-medium transition-all"
                      >
                        🫀 Not breathing
                      </button>
                      <button 
                        onClick={() => triggerQuickUpdate("We have secured the traffic scene.")}
                        className="bg-slateSurface hover:bg-[#2A2E45] border border-gray-800 rounded-xl px-3 py-1.5 text-xs text-textWhite font-medium transition-all"
                      >
                        🚗 Scene is safe
                      </button>
                    </div>
                  </div>
                </div>

                {/* 4. ACTIVE RESCUE LOGS TIMELINE */}
                <div className="bg-slateSurface rounded-3xl p-5 border border-gray-800 shadow-xl space-y-4">
                  <div className="flex items-center justify-between pb-2 border-b border-gray-800">
                    <span className="text-xs font-extrabold text-white uppercase tracking-wider">Rescue Timeline Logs</span>
                    <span className="text-[10px] bg-slateDark border border-gray-800 py-1 px-2.5 rounded-full font-bold text-gray-500">
                      {sessionLogs.length} Events Logged
                    </span>
                  </div>

                  <div className="max-h-[220px] overflow-y-auto space-y-3 pr-1">
                    {sessionLogs.map(log => (
                      <div 
                        key={log.id} 
                        className={`p-3.5 rounded-2xl text-xs space-y-1 ${
                          log.sender === 'Bystander' 
                            ? 'bg-slateDark border border-gray-800 ml-8' 
                            : log.isWarning 
                              ? 'bg-emergencyRed/5 border border-emergencyRed/20 mr-8' 
                              : 'bg-safeGreen/5 border border-safeGreen/20 mr-8'
                        }`}
                      >
                        <div className="flex items-center justify-between text-[10px] font-extrabold">
                          <span className={log.sender === 'Bystander' ? 'text-[#1E88E5]' : log.isWarning ? 'text-emergencyRed' : 'text-safeGreen'}>
                            {log.sender.toUpperCase()}
                          </span>
                          <span className="text-gray-500">{log.timestamp}</span>
                        </div>
                        <p className="text-textWhite leading-relaxed">{log.message}</p>
                      </div>
                    ))}
                    <div ref={chatLogsEndRef} />
                  </div>
                </div>

              </div>
            )}

            {/* -------------------- BOOKINGS VIEW TAB -------------------- */}
            {activeTab === 'BOOKINGS' && (
              <div className="space-y-6 animate-fade-in flex flex-col h-full min-h-[500px]">
                
                {/* Simulated Bolt-style interactive map canvas */}
                <div className="relative rounded-3xl overflow-hidden border border-gray-800 shadow-2xl h-[280px]">
                  <canvas 
                    ref={canvasRef} 
                    width={450} 
                    height={280} 
                    className="w-full h-full block"
                  />

                  {/* HUD overlay labels */}
                  <div className="absolute top-4 left-4 bg-slateSurface border border-gray-800 py-1 px-3 rounded-full flex items-center gap-1.5 shadow-lg">
                    <div className="w-2 h-2 rounded-full bg-safeGreen animate-ping" />
                    <span className="text-[9px] font-extrabold text-white tracking-wider">BOLT GPS SYNC: ACTIVE</span>
                  </div>

                  <button 
                    onClick={() => setIsMapSatellite(prev => !prev)}
                    className="absolute top-4 right-4 bg-slateSurface hover:bg-slateSurfaceLight p-2 rounded-xl border border-gray-800 text-white shadow-lg transition-all"
                  >
                    <RefreshCw size={14} />
                  </button>
                </div>

                {/* Live responder metadata card */}
                <div className="bg-slateSurface rounded-3xl p-5 border border-gray-800 shadow-xl space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-[10px] text-safeGreen font-extrabold tracking-widest block uppercase">Decentralized Responder</span>
                      <h3 className="text-base font-extrabold text-white mt-1">{responderName}</h3>
                    </div>

                    <span className="bg-safeGreen/15 text-safeGreen font-black text-xs px-3.5 py-1.5 rounded-full tracking-wider">
                      {responderMinutes > 0 ? `${responderMinutes} MINS` : "ARRIVED"}
                    </span>
                  </div>

                  <div className="h-[1px] bg-gray-800/80" />

                  {/* Route Stats Grid */}
                  <div className="grid grid-cols-3 gap-4 text-center">
                    <div>
                      <span className="text-[9px] text-textGray font-extrabold tracking-wider block uppercase">Distance</span>
                      <strong className="text-sm text-white mt-1 block">{responderDistance} KM</strong>
                    </div>
                    <div>
                      <span className="text-[9px] text-textGray font-extrabold tracking-wider block uppercase">Speed</span>
                      <strong className="text-sm text-white mt-1 block">45 km/h</strong>
                    </div>
                    <div>
                      <span className="text-[9px] text-textGray font-extrabold tracking-wider block uppercase">Vehicle</span>
                      <strong className="text-sm text-white mt-1 block">Okada (Plate TL-29B)</strong>
                    </div>
                  </div>

                  {/* Interactive mock buttons */}
                  <div className="flex gap-3 pt-2">
                    <button 
                      onClick={() => showToast("Calling Emeka Okafor (TouchLife Responder)...")}
                      className="flex-1 py-3.5 bg-safeGreen hover:bg-green-600 font-bold text-white text-xs rounded-xl shadow-lg transition-all flex items-center justify-center gap-2"
                    >
                      <PhoneCall size={14} />
                      Call Responder
                    </button>
                    <button 
                      onClick={() => showToast("Opening emergency chat with responder...")}
                      className="flex-1 py-3.5 bg-slateSurfaceLight hover:bg-[#343956] font-bold text-white text-xs rounded-xl border border-gray-700 transition-all flex items-center justify-center gap-2"
                    >
                      <MessageSquare size={14} />
                      Message
                    </button>
                  </div>
                </div>

              </div>
            )}

            {/* -------------------- AI COACH TAB -------------------- */}
            {activeTab === 'AI_COACH' && (
              <div className="space-y-6 animate-fade-in text-center py-4">
                
                {/* Glowing pulsating orb (Gemini Live style) */}
                <div className="bg-slateSurface rounded-3xl p-6 border border-gray-800 shadow-xl space-y-6">
                  <div>
                    <span className="text-[10px] text-accentAmber font-extrabold tracking-widest uppercase block mb-1">GEMINI LIVE COACH</span>
                    <h3 className="text-lg font-extrabold text-white">Vocal AI Rescue Companion</h3>
                    <p className="text-xs text-textGray leading-relaxed max-w-xs mx-auto mt-2">Simulating high-stress medical logic to assist first-responders in Nigeria.</p>
                  </div>

                  {/* Orb component with visual reactive styles */}
                  <div className="flex items-center justify-center py-4">
                    <div className="relative w-36 h-36 flex items-center justify-center">
                      <div className={`absolute inset-0 rounded-full bg-gradient-to-r from-accentAmber to-safeGreen opacity-20 transition-all duration-300 ${isSpeaking ? 'animate-ping' : ''}`} />
                      <div className={`absolute w-28 h-28 rounded-full bg-gradient-to-r from-accentAmber to-safeGreen opacity-30 transition-all duration-300 ${isSpeaking ? 'scale-110' : 'scale-100'}`} />
                      
                      <button 
                        onClick={replayInstruction}
                        className="w-20 h-20 rounded-full bg-slateDark border border-gray-800 text-white flex items-center justify-center z-10 hover:border-accentAmber shadow-2xl transition-all"
                      >
                        <Volume2 size={32} className={`text-accentAmber ${isSpeaking ? 'animate-pulse' : ''}`} />
                      </button>
                    </div>
                  </div>

                  <p className="text-xs text-textGray italic">
                    {isSpeaking ? "Coach is speaking step-by-step guidance..." : "Coach is standby. Click the speaker to replay."}
                  </p>

                  <div className="flex justify-center">
                    <button 
                      onClick={resetSession}
                      className="py-2.5 px-6 rounded-xl bg-[#12141C] hover:bg-slateDark border border-gray-800 text-accentAmber font-bold text-xs flex items-center justify-center gap-2 transition-all"
                    >
                      <RotateCcw size={14} />
                      Restart Emergency Guidance Session
                    </button>
                  </div>
                </div>

                {/* Config credentials */}
                <div className="bg-slateSurface rounded-3xl p-5 border border-gray-800 text-left space-y-3">
                  <span className="text-[10px] text-safeGreen font-extrabold tracking-widest uppercase block">AI Configuration</span>
                  <p className="text-xs text-textGray leading-relaxed">TouchLife is integrated with Gemini 3.5 Flash. Insert your custom API key to unlock the server-side model, or leave empty to use our local rule-based safety core.</p>
                  
                  <div className="flex gap-2">
                    <input 
                      type="password"
                      placeholder="Insert Gemini API Key"
                      value={userApiKey}
                      onChange={(e) => setUserApiKey(e.target.value)}
                      className="flex-1 bg-[#12141C] text-white border border-gray-800 rounded-xl py-2 px-3 text-xs outline-none focus:border-safeGreen"
                    />
                    <button 
                      onClick={() => showToast("Gemini API Key configured!")}
                      className="bg-safeGreen hover:bg-green-600 text-white font-bold text-xs py-2 px-4 rounded-xl shadow-lg transition-all"
                    >
                      Save Key
                    </button>
                  </div>
                </div>

              </div>
            )}

            {/* -------------------- PROFILE TAB -------------------- */}
            {activeTab === 'PROFILE' && (
              <div className="space-y-6 animate-fade-in">
                
                {profileSection === 'MAIN' && (
                  <div className="space-y-4">
                    {/* User Profile Info card */}
                    <div className="bg-slateSurface rounded-3xl p-5 border border-gray-800 shadow-xl flex items-center gap-4">
                      <div className="w-14 h-14 rounded-full bg-safeGreen flex items-center justify-center text-white text-xl font-bold uppercase shadow-inner">
                        {userName ? userName.charAt(0) : "G"}
                      </div>
                      <div>
                        <h3 className="text-base font-extrabold text-white">{userName || "Guest Bystander"}</h3>
                        <span className="text-[10px] text-accentAmber font-extrabold tracking-wider uppercase block mt-0.5">Role: {userRole}</span>
                        <p className="text-xs text-textGray mt-0.5">{userPhone || "No registered phone attached"}</p>
                      </div>
                    </div>

                    {/* Verification / Settings list items */}
                    <div className="space-y-3">
                      <button 
                        onClick={() => setProfileSection('VERIFY_EMAIL')}
                        className="w-full bg-slateSurface border border-gray-800 hover:bg-slateSurfaceLight rounded-2xl p-4 flex items-center justify-between text-left transition-all"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-xl bg-slateDark flex items-center justify-center text-accentAmber">
                            <Mail size={16} />
                          </div>
                          <div>
                            <strong className="text-sm text-white block">Verify Email Address</strong>
                            <span className="text-xs text-textGray">{userEmail || "No email address configured"}</span>
                          </div>
                        </div>
                        <ChevronRight size={16} className="text-textGray" />
                      </button>

                      <button 
                        onClick={() => setProfileSection('CHANGE_PASSWORD')}
                        className="w-full bg-slateSurface border border-gray-800 hover:bg-slateSurfaceLight rounded-2xl p-4 flex items-center justify-between text-left transition-all"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-xl bg-slateDark flex items-center justify-center text-[#1E88E5]">
                            <Key size={16} />
                          </div>
                          <div>
                            <strong className="text-sm text-white block">Reset Security Credentials</strong>
                            <span className="text-xs text-textGray">Configure password protection</span>
                          </div>
                        </div>
                        <ChevronRight size={16} className="text-textGray" />
                      </button>

                      {/* Theme toggle */}
                      <div className="w-full bg-slateSurface border border-gray-800 rounded-2xl p-4 flex items-center justify-between text-left">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-xl bg-slateDark flex items-center justify-center text-safeGreen">
                            <Activity size={16} />
                          </div>
                          <div>
                            <strong className="text-sm text-white block">Dark Slate Theme</strong>
                            <span className="text-xs text-textGray">Optimized for low-light night rescues</span>
                          </div>
                        </div>
                        
                        <button
                          onClick={() => {
                            setIsDarkTheme(prev => !prev);
                            showToast(`Theme toggled!`);
                          }}
                          className={`w-12 h-6 rounded-full p-0.5 transition-colors duration-300 ${isDarkTheme ? 'bg-safeGreen' : 'bg-gray-400'}`}
                        >
                          <div className={`w-5 h-5 bg-white rounded-full transition-transform duration-300 ${isDarkTheme ? 'translate-x-6' : 'translate-x-0'}`} />
                        </button>
                      </div>
                    </div>

                    {/* Logout and Exit buttons */}
                    <div className="pt-4 space-y-3">
                      <button 
                        onClick={() => {
                          setCurrentScreen('ONBOARDING');
                          setShowSignIn(true);
                          showToast("Logged out of session.");
                        }}
                        className="w-full py-3.5 bg-slateSurface hover:bg-[#2A2E45] border border-gray-800 text-white font-bold text-sm rounded-xl transition-all shadow-lg"
                      >
                        Logout and Re-register
                      </button>
                    </div>
                  </div>
                )}

                {/* Subsection: Verify email */}
                {profileSection === 'VERIFY_EMAIL' && (
                  <div className="space-y-4">
                    <h3 className="text-base font-extrabold text-white">Verify Email Address</h3>
                    <p className="text-xs text-textGray leading-relaxed">Enter your registered email address below. We'll verify your first-aid credentials with Nigerian clinical volunteer lists.</p>
                    
                    <input 
                      type="email" 
                      placeholder="e.g. name@example.com"
                      value={userEmail}
                      onChange={(e) => setUserEmail(e.target.value)}
                      className="w-full bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-xl py-3 px-4 text-sm outline-none outline-0"
                    />

                    <div className="flex gap-3">
                      <button 
                        onClick={() => setProfileSection('MAIN')}
                        className="flex-1 py-3 bg-slateSurface hover:bg-slateSurfaceLight border border-gray-800 rounded-xl font-bold text-xs text-textGray"
                      >
                        Cancel
                      </button>
                      <button 
                        onClick={() => {
                          setProfileSection('MAIN');
                          showToast("Email address verification pending.");
                        }}
                        className="flex-1 py-3 bg-safeGreen hover:bg-green-600 rounded-xl font-bold text-xs text-white"
                      >
                        Confirm Email
                      </button>
                    </div>
                  </div>
                )}

                {/* Subsection: Change password */}
                {profileSection === 'CHANGE_PASSWORD' && (
                  <div className="space-y-4">
                    <h3 className="text-base font-extrabold text-white">Reset Security Password</h3>
                    <p className="text-xs text-[#9E9E9E]">Secure your TouchLife account. Keep your emergency rescue capabilities protected by using a strong password.</p>
                    
                    <input 
                      type="password" 
                      placeholder="New Security Password"
                      value={userPassword}
                      onChange={(e) => setUserPassword(e.target.value)}
                      className="w-full bg-slateSurface text-white border border-gray-800 focus:border-safeGreen rounded-xl py-3 px-4 text-sm outline-none outline-0"
                    />

                    <div className="flex gap-3">
                      <button 
                        onClick={() => setProfileSection('MAIN')}
                        className="flex-1 py-3 bg-slateSurface hover:bg-slateSurfaceLight border border-gray-800 rounded-xl font-bold text-xs text-textGray"
                      >
                        Cancel
                      </button>
                      <button 
                        onClick={() => {
                          setProfileSection('MAIN');
                          showToast("Security password updated!");
                        }}
                        className="flex-1 py-3 bg-safeGreen hover:bg-green-600 rounded-xl font-bold text-xs text-white"
                      >
                        Update Password
                      </button>
                    </div>
                  </div>
                )}

              </div>
            )}

          </main>

          {/* Bottom Navigation Bar */}
          <nav className={`fixed bottom-0 left-0 right-0 max-w-lg mx-auto w-full border-t flex justify-around py-3 px-4 z-40 backdrop-blur-md ${isDarkTheme ? 'bg-[#12141C]/90 border-gray-800/80' : 'bg-white/90 border-gray-200'}`}>
            <button 
              onClick={() => { setActiveTab('HOME'); setProfileSection('MAIN'); }}
              className={`flex flex-col items-center gap-1 transition-all ${activeTab === 'HOME' ? 'text-safeGreen' : 'text-textGray'}`}
            >
              <Heart size={20} fill={activeTab === 'HOME' ? 'currentColor' : 'none'} />
              <span className="text-[9px] font-extrabold tracking-wider uppercase">Coach</span>
            </button>

            <button 
              onClick={() => { setActiveTab('BOOKINGS'); setProfileSection('MAIN'); }}
              className={`flex flex-col items-center gap-1 transition-all ${activeTab === 'BOOKINGS' ? 'text-safeGreen' : 'text-textGray'}`}
            >
              <MapPin size={20} fill={activeTab === 'BOOKINGS' ? 'currentColor' : 'none'} />
              <span className="text-[9px] font-extrabold tracking-wider uppercase">Bookings</span>
            </button>

            <button 
              onClick={() => { setActiveTab('AI_COACH'); setProfileSection('MAIN'); }}
              className={`flex flex-col items-center gap-1 transition-all ${activeTab === 'AI_COACH' ? 'text-safeGreen' : 'text-textGray'}`}
            >
              <Activity size={20} />
              <span className="text-[9px] font-extrabold tracking-wider uppercase">Gemini</span>
            </button>

            <button 
              onClick={() => { setActiveTab('PROFILE'); setProfileSection('MAIN'); }}
              className={`flex flex-col items-center gap-1 transition-all ${activeTab === 'PROFILE' ? 'text-safeGreen' : 'text-textGray'}`}
            >
              <User size={20} fill={activeTab === 'PROFILE' ? 'currentColor' : 'none'} />
              <span className="text-[9px] font-extrabold tracking-wider uppercase">Profile</span>
            </button>
          </nav>

        </div>
      )}

    </div>
  );
}
