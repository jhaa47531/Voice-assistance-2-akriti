import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const APK_PATH = path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const NOTES_FILE = path.join(__dirname, '.voice_notes.json');

// Helper to load or save notes
function getNotes(): Array<{ id: string; text: string; timestamp: string }> {
  try {
    if (fs.existsSync(NOTES_FILE)) {
      return JSON.parse(fs.readFileSync(NOTES_FILE, 'utf-8'));
    }
  } catch (e) {
    console.error('Error reading notes:', e);
  }
  return [
    { id: '1', text: 'Remember to test Akriti voice conversation on Android device', timestamp: new Date().toLocaleTimeString() }
  ];
}

function saveNotes(notes: Array<{ id: string; text: string; timestamp: string }>) {
  try {
    fs.writeFileSync(NOTES_FILE, JSON.stringify(notes, null, 2));
  } catch (e) {
    console.error('Error saving notes:', e);
  }
}

// Call Gemini API using native fetch
async function callGemini(prompt: string, history: Array<{ role: string; text: string }> = [], imageBase64?: string, mimeType?: string): Promise<string> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return "Gemini API key is not configured. I am operating in offline conversational mode.";
  }

  const systemInstruction = {
    parts: [
      {
        text: "You are Akriti, an intelligent, empathetic, and highly capable personal AI voice assistant. You respond concisely and conversationally, ideal for spoken audio responses (short paragraphs, natural cadence, friendly and direct). You support multilingual dialogues, phone control actions, note-taking, and visual analysis."
      }
    ]
  };

  const contents: any[] = [];
  
  // Add recent history (up to last 6 messages)
  for (const msg of history.slice(-6)) {
    contents.push({
      role: msg.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: msg.text }]
    });
  }

  const currentParts: any[] = [];
  if (imageBase64 && mimeType) {
    currentParts.push({
      inlineData: {
        mimeType: mimeType,
        data: imageBase64
      }
    });
  }
  currentParts.push({ text: prompt });

  contents.push({
    role: 'user',
    parts: currentParts
  });

  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        systemInstruction,
        contents,
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 800
        }
      })
    });

    if (!res.ok) {
      const errText = await res.text();
      console.error('Gemini API error:', res.status, errText);
      return `I encountered an issue connecting with Gemini (${res.status}). Let me help you locally.`;
    }

    const data = (await res.json()) as any;
    const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    return text || "I didn't catch that. Could you please repeat?";
  } catch (error: any) {
    console.error('Fetch error:', error);
    return `Connection error: ${error.message}.`;
  }
}

// Read body helper
function readBody(req: http.IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 10 * 1024 * 1024) {
        reject(new Error('Body too large'));
      }
    });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const reqUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const pathname = reqUrl.pathname;

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, DELETE');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Health check
  if (pathname === '/health' || pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'healthy', app: 'Akriti', timestamp: new Date().toISOString() }));
    return;
  }

  // APK Info
  if (pathname === '/api/apk-info') {
    try {
      if (fs.existsSync(APK_PATH)) {
        const stats = fs.statSync(APK_PATH);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          exists: true,
          sizeMb: (stats.size / (1024 * 1024)).toFixed(1),
          modified: stats.mtime.toISOString(),
          filename: 'app-debug.apk'
        }));
        return;
      }
    } catch (e) {}
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ exists: false, filename: 'app-debug.apk' }));
    return;
  }

  // APK Download
  if (pathname === '/api/download-apk') {
    if (fs.existsSync(APK_PATH)) {
      const stat = fs.statSync(APK_PATH);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Length': stat.size,
        'Content-Disposition': 'attachment; filename="akriti-v3-debug.apk"'
      });
      const stream = fs.createReadStream(APK_PATH);
      stream.pipe(res);
      return;
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'APK build file not found' }));
      return;
    }
  }

  // Notes API
  if (pathname === '/api/notes') {
    if (req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ notes: getNotes() }));
      return;
    }
    if (req.method === 'POST') {
      try {
        const raw = await readBody(req);
        const { text } = JSON.parse(raw);
        if (!text) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Text required' }));
          return;
        }
        const notes = getNotes();
        const newNote = {
          id: Date.now().toString(),
          text,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        notes.unshift(newNote);
        saveNotes(notes);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ note: newNote, notes }));
        return;
      } catch (e: any) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: e.message }));
        return;
      }
    }
    if (req.method === 'DELETE') {
      const id = reqUrl.searchParams.get('id');
      if (id) {
        let notes = getNotes().filter(n => n.id !== id);
        saveNotes(notes);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, notes }));
        return;
      }
    }
  }

  // Chat API
  if (pathname === '/api/chat' && req.method === 'POST') {
    try {
      const raw = await readBody(req);
      const data = JSON.parse(raw);
      const prompt = data.message || '';
      const history = data.history || [];
      const reply = await callGemini(prompt, history);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ reply }));
      return;
    } catch (e: any) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: e.message }));
      return;
    }
  }

  // Vision API
  if (pathname === '/api/vision' && req.method === 'POST') {
    try {
      const raw = await readBody(req);
      const data = JSON.parse(raw);
      const prompt = data.prompt || 'Describe what you see in this image and offer helpful insights.';
      const imageBase64 = data.imageBase64;
      const mimeType = data.mimeType || 'image/jpeg';
      const reply = await callGemini(prompt, [], imageBase64, mimeType);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ reply }));
      return;
    } catch (e: any) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: e.message }));
      return;
    }
  }

  // Serve Single-Page Web App UI
  if (pathname === '/' || pathname === '/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(getHtmlContent());
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not found');
});

function getHtmlContent() {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Akriti - Personal AI Voice Assistant</title>
  <meta name="description" content="Personal AI voice assistant with multilingual dialogue, multimodal visual Q&A, voice notes, and safe phone control.">
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&family=Playfair+Display:wght@600;700&display=swap" rel="stylesheet">
  <style>
    body {
      font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
    }
    .serif-title {
      font-family: 'Playfair Display', Georgia, serif;
    }
    @keyframes pulse-glow {
      0%, 100% { transform: scale(1); opacity: 0.85; filter: drop-shadow(0 0 15px rgba(99, 102, 241, 0.4)); }
      50% { transform: scale(1.08); opacity: 1; filter: drop-shadow(0 0 25px rgba(168, 85, 247, 0.7)); }
    }
    @keyframes wave-bar {
      0%, 100% { height: 8px; }
      50% { height: 32px; }
    }
    .orb-pulse {
      animation: pulse-glow 3s infinite ease-in-out;
    }
    .wave-anim-1 { animation: wave-bar 1.1s infinite ease-in-out; }
    .wave-anim-2 { animation: wave-bar 0.9s infinite ease-in-out 0.2s; }
    .wave-anim-3 { animation: wave-bar 1.3s infinite ease-in-out 0.4s; }
    .wave-anim-4 { animation: wave-bar 0.8s infinite ease-in-out 0.1s; }
    .wave-anim-5 { animation: wave-bar 1.2s infinite ease-in-out 0.3s; }
  </style>
</head>
<body class="bg-slate-950 text-slate-100 min-h-screen flex flex-col antialiased selection:bg-indigo-500 selection:text-white">

  <!-- Top App Bar -->
  <header id="app-header" class="border-b border-slate-800/80 bg-slate-900/60 backdrop-blur sticky top-0 z-30 px-4 lg:px-8 py-3.5 flex items-center justify-between">
    <div class="flex items-center gap-3">
      <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-md shadow-indigo-500/20">
        <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 003-3V5a3 3 0 10-6 0v6a3 3 0 003 3z"></path>
        </svg>
      </div>
      <div>
        <h1 class="text-lg font-bold tracking-tight text-white flex items-center gap-2">
          Akriti
          <span class="text-xs px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 font-medium border border-indigo-500/30">V3 Voice AI</span>
        </h1>
        <p class="text-xs text-slate-400">Multilingual Assistant & Android Companion</p>
      </div>
    </div>

    <!-- Status & APK Action -->
    <div class="flex items-center gap-3">
      <div id="status-indicator" class="hidden sm:flex items-center gap-2 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-xs text-emerald-400 font-medium">
        <span class="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
        <span>Online</span>
      </div>
      <a id="btn-download-apk" href="/api/download-apk" class="flex items-center gap-2 text-xs font-semibold px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 transition text-white shadow-sm shadow-indigo-600/30">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
        </svg>
        <span class="whitespace-nowrap">Download APK (22MB)</span>
      </a>
    </div>
  </header>

  <!-- Main Layout -->
  <main class="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8 grid grid-cols-1 lg:grid-cols-12 gap-6">

    <!-- Left / Center: Voice Assistant Main Screen (7 cols) -->
    <section class="lg:col-span-7 flex flex-col gap-4">
      
      <!-- Interactive Assistant Stage Card -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-2xl p-6 sm:p-8 flex flex-col items-center text-center relative overflow-hidden shadow-xl">
        <div class="absolute inset-0 bg-gradient-to-b from-indigo-500/5 via-transparent to-transparent pointer-events-none"></div>

        <!-- Assistant Voice Sphere / Visualizer -->
        <div class="my-4 relative flex items-center justify-center">
          <div id="voice-aura" class="w-28 h-28 sm:w-32 sm:h-32 rounded-full bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 orb-pulse flex items-center justify-center shadow-2xl shadow-indigo-500/30 transition-all duration-500">
            <div class="w-24 h-24 sm:w-28 sm:h-28 rounded-full bg-slate-950/80 flex items-center justify-center backdrop-blur-sm">
              <svg id="assistant-icon" class="w-10 h-10 text-indigo-400 transition" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 003-3V5a3 3 0 10-6 0v6a3 3 0 003 3z"></path>
              </svg>
            </div>
          </div>
          <!-- Sound wave bars (active while listening / speaking) -->
          <div id="wave-bars" class="hidden absolute flex items-center gap-1.5 h-12">
            <span class="w-1.5 bg-indigo-400 rounded-full wave-anim-1"></span>
            <span class="w-1.5 bg-purple-400 rounded-full wave-anim-2"></span>
            <span class="w-1.5 bg-pink-400 rounded-full wave-anim-3"></span>
            <span class="w-1.5 bg-purple-400 rounded-full wave-anim-4"></span>
            <span class="w-1.5 bg-indigo-400 rounded-full wave-anim-5"></span>
          </div>
        </div>

        <div class="max-w-md mx-auto mb-6">
          <h2 id="assistant-status-title" class="serif-title text-2xl font-bold text-white mb-2">Hello, I'm Akriti</h2>
          <p id="assistant-status-subtitle" class="text-sm text-slate-300 leading-relaxed">
            Tap the microphone to speak, or type below. I can converse in multiple languages, analyze images, take notes, and manage phone tasks.
          </p>
        </div>

        <!-- Quick Interaction Buttons / Language Selector -->
        <div class="flex flex-wrap items-center justify-center gap-2 mb-6">
          <button onclick="setLanguage('en-US', 'English')" class="lang-btn px-3 py-1 rounded-full text-xs font-medium bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 hover:bg-indigo-500/30 transition">English</button>
          <button onclick="setLanguage('hi-IN', 'हिन्दी (Hindi)')" class="lang-btn px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 hover:bg-slate-700 transition">हिन्दी</button>
          <button onclick="setLanguage('es-ES', 'Español')" class="lang-btn px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 hover:bg-slate-700 transition">Español</button>
          <button onclick="setLanguage('fr-FR', 'Français')" class="lang-btn px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 hover:bg-slate-700 transition">Français</button>
          <button onclick="setLanguage('de-DE', 'Deutsch')" class="lang-btn px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 hover:bg-slate-700 transition">Deutsch</button>
        </div>

        <!-- Microphone Trigger Button -->
        <div class="flex items-center gap-4">
          <button id="btn-mic" onclick="toggleVoiceInput()" class="relative group p-4 rounded-full bg-gradient-to-r from-indigo-600 to-purple-600 text-white font-medium shadow-lg shadow-indigo-500/30 hover:shadow-indigo-500/50 hover:scale-105 active:scale-95 transition">
            <svg id="mic-icon" class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 003-3V5a3 3 0 10-6 0v6a3 3 0 003 3z"></path>
            </svg>
          </button>
          <button id="btn-tts-toggle" onclick="toggleTts()" class="p-3 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 text-xs flex items-center gap-2 transition" title="Toggle audio readback">
            <svg id="speaker-icon" class="w-4 h-4 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z"></path>
            </svg>
            <span id="tts-label">Voice Output: ON</span>
          </button>
        </div>
      </div>

      <!-- Dialogue History -->
      <div class="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 flex flex-col h-80 flex-1 overflow-hidden shadow-lg">
        <div class="flex items-center justify-between pb-3 border-b border-slate-800 mb-3">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">Live Dialogue</span>
          <button onclick="clearChat()" class="text-xs text-slate-500 hover:text-slate-300 transition">Clear</button>
        </div>

        <div id="chat-stream" class="flex-1 overflow-y-auto space-y-3.5 pr-2">
          <!-- Initial Greeting -->
          <div class="flex items-start gap-3">
            <div class="w-7 h-7 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center text-xs font-bold shrink-0">A</div>
            <div class="bg-slate-800/90 rounded-2xl rounded-tl-none p-3 text-sm text-slate-200 max-w-[85%] border border-slate-700/60 shadow-sm">
              Namaste! I am Akriti. How can I help you today? You can talk to me, upload an image for analysis, or ask me to control phone actions.
            </div>
          </div>
        </div>

        <!-- Input Bar with File Upload -->
        <div class="pt-3 border-t border-slate-800 flex items-center gap-2">
          <!-- Image upload input -->
          <label class="p-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white cursor-pointer border border-slate-700 transition" title="Attach image for Multimodal Visual Q&A">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
            </svg>
            <input type="file" id="image-input" accept="image/*" class="hidden" onchange="handleImageSelected(event)">
          </label>

          <input type="text" id="text-input" placeholder="Type a message or command..." 
            class="flex-1 bg-slate-950 border border-slate-700/80 rounded-xl px-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
            onkeydown="if(event.key==='Enter') sendTextMessage()">

          <button onclick="sendTextMessage()" class="p-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white transition shadow-sm shadow-indigo-600/30">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"></path>
            </svg>
          </button>
        </div>

        <div id="image-preview-bar" class="hidden pt-2 flex items-center gap-2">
          <span class="text-xs text-indigo-400">Attached image:</span>
          <span id="image-preview-name" class="text-xs text-slate-300 truncate max-w-xs font-mono"></span>
          <button onclick="clearAttachedImage()" class="text-xs text-red-400 hover:text-red-300">Remove</button>
        </div>
      </div>
    </section>

    <!-- Right: Companion Controls, Voice Notes & Phone Actions (5 cols) -->
    <aside class="lg:col-span-5 flex flex-col gap-6">

      <!-- Android Companion Status & APK Download Card -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 shadow-lg">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
              <svg class="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9996.4482.9996.9993.0001.5511-.4486.9997-.9996.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9997.4482.9997.9993 0 .5511-.4486.9997-.9997.9997m11.4045-6.02l1.9973-3.4592a.416.416 0 00-.1521-.5676.416.416 0 00-.5676.1521l-2.0223 3.503C15.5902 8.4116 13.8533 8 12 8s-3.5902.4116-5.1368 1.1507L4.8409 5.6477a.4161.4161 0 00-.5677-.1521.4157.4157 0 00-.1521.5676l1.9973 3.4592C2.6889 11.1867.3432 14.6589 0 18.761h24c-.3432-4.1021-2.6889-7.5743-6.1185-9.4396"/>
              </svg>
            </div>
            <div>
              <h3 class="text-sm font-semibold text-white">Android Application</h3>
              <p class="text-xs text-slate-400">Jetpack Compose • Kotlin • Gradle 9.3</p>
            </div>
          </div>
          <span class="px-2 py-0.5 text-xs rounded-full bg-emerald-500/20 text-emerald-300 font-medium border border-emerald-500/30">Ready</span>
        </div>

        <div class="bg-slate-950/70 rounded-xl p-3.5 border border-slate-800 text-xs space-y-2 mb-4">
          <div class="flex justify-between text-slate-400">
            <span>Package Name:</span>
            <span class="text-slate-200 font-mono">com.example.akriti</span>
          </div>
          <div class="flex justify-between text-slate-400">
            <span>Compiled Binary:</span>
            <span class="text-emerald-400 font-semibold font-mono">app-debug.apk (22 MB)</span>
          </div>
          <div class="flex justify-between text-slate-400">
            <span>Target SDK:</span>
            <span class="text-slate-200">Android 36 (VanillaIceCream)</span>
          </div>
        </div>

        <a href="/api/download-apk" class="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold transition border border-slate-700 shadow-sm">
          <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          Download Signed Debug APK
        </a>
      </div>

      <!-- Quick Safe Phone Actions Simulation -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 shadow-lg">
        <h3 class="text-sm font-semibold text-white mb-1">Phone Controls (Voice/Tap)</h3>
        <p class="text-xs text-slate-400 mb-4">Safe device utilities triggered by Akriti voice commands</p>

        <div class="grid grid-cols-2 gap-2.5 text-xs">
          <!-- Flashlight -->
          <button onclick="toggleDeviceAction('Flashlight')" id="action-flashlight" class="p-3 rounded-xl bg-slate-950/80 border border-slate-800 hover:border-indigo-500/50 flex items-center gap-2.5 transition text-left">
            <span class="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">⚡</span>
            <div>
              <p class="font-medium text-slate-200">Flashlight</p>
              <p id="state-flashlight" class="text-[11px] text-slate-400">Off</p>
            </div>
          </button>

          <!-- Wi-Fi -->
          <button onclick="toggleDeviceAction('Wi-Fi')" id="action-wifi" class="p-3 rounded-xl bg-slate-950/80 border border-slate-800 hover:border-indigo-500/50 flex items-center gap-2.5 transition text-left">
            <span class="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">📶</span>
            <div>
              <p class="font-medium text-slate-200">Wi-Fi</p>
              <p id="state-wifi" class="text-[11px] text-emerald-400">Connected</p>
            </div>
          </button>

          <!-- Battery Saver -->
          <button onclick="toggleDeviceAction('Battery Saver')" id="action-battery" class="p-3 rounded-xl bg-slate-950/80 border border-slate-800 hover:border-indigo-500/50 flex items-center gap-2.5 transition text-left">
            <span class="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">🔋</span>
            <div>
              <p class="font-medium text-slate-200">Battery Saver</p>
              <p id="state-battery" class="text-[11px] text-slate-400">84% Normal</p>
            </div>
          </button>

          <!-- Set Alarm -->
          <button onclick="simulateAlarm()" class="p-3 rounded-xl bg-slate-950/80 border border-slate-800 hover:border-indigo-500/50 flex items-center gap-2.5 transition text-left">
            <span class="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">⏰</span>
            <div>
              <p class="font-medium text-slate-200">Set Alarm</p>
              <p id="state-alarm" class="text-[11px] text-slate-400">7:00 AM</p>
            </div>
          </button>
        </div>
      </div>

      <!-- Voice Notes Card -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 shadow-lg flex flex-col flex-1">
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <svg class="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 003-3V5a3 3 0 10-6 0v6a3 3 0 003 3z"></path>
            </svg>
            <h3 class="text-sm font-semibold text-white">Voice Notes</h3>
          </div>
          <button onclick="addVoiceNotePrompt()" class="text-xs px-2.5 py-1 rounded-lg bg-purple-600/20 text-purple-300 hover:bg-purple-600/30 border border-purple-500/30 transition">
            + New Note
          </button>
        </div>

        <div id="notes-list" class="space-y-2 overflow-y-auto max-h-48 pr-1 flex-1">
          <!-- Loaded dynamically -->
          <div class="text-xs text-slate-500 py-3 text-center">Loading voice notes...</div>
        </div>
      </div>

    </aside>
  </main>

  <script>
    let recognition = null;
    let isListening = false;
    let ttsEnabled = true;
    let currentLanguage = 'en-US';
    let chatHistory = [];
    let attachedImageBase64 = null;
    let attachedImageMime = null;

    // Initialize Web Speech Recognition
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
      const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
      recognition = new SpeechRec();
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.lang = currentLanguage;

      recognition.onstart = () => {
        isListening = true;
        updateVoiceUiState('listening');
      };

      recognition.onresult = (event) => {
        const transcript = event.results[0][0].transcript;
        document.getElementById('text-input').value = transcript;
        sendTextMessage();
      };

      recognition.onerror = (event) => {
        console.warn('Speech recognition error:', event.error);
        updateVoiceUiState('idle');
      };

      recognition.onend = () => {
        isListening = false;
        updateVoiceUiState('idle');
      };
    }

    function setLanguage(langCode, langName) {
      currentLanguage = langCode;
      if (recognition) recognition.lang = langCode;
      document.querySelectorAll('.lang-btn').forEach(btn => {
        if (btn.textContent.includes(langName) || langName.includes(btn.textContent)) {
          btn.className = 'lang-btn px-3 py-1 rounded-full text-xs font-medium bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 transition';
        } else {
          btn.className = 'lang-btn px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 hover:bg-slate-700 transition';
        }
      });
      speakText('Language switched to ' + langName);
    }

    function toggleVoiceInput() {
      if (!recognition) {
        alert('Speech recognition is not supported in this browser. You can type in the input bar below!');
        return;
      }
      if (isListening) {
        recognition.stop();
      } else {
        recognition.start();
      }
    }

    function updateVoiceUiState(state) {
      const aura = document.getElementById('voice-aura');
      const waveBars = document.getElementById('wave-bars');
      const micBtn = document.getElementById('btn-mic');
      const title = document.getElementById('assistant-status-title');
      const subtitle = document.getElementById('assistant-status-subtitle');

      if (state === 'listening') {
        aura.classList.add('scale-110');
        waveBars.classList.remove('hidden');
        micBtn.classList.add('ring-4', 'ring-indigo-400');
        title.textContent = "Listening...";
        subtitle.textContent = "Akriti is listening to your voice in " + currentLanguage;
      } else if (state === 'thinking') {
        aura.classList.add('animate-spin');
        waveBars.classList.remove('hidden');
        title.textContent = "Thinking...";
        subtitle.textContent = "Consulting Gemini AI...";
      } else if (state === 'speaking') {
        aura.classList.remove('animate-spin');
        waveBars.classList.remove('hidden');
        title.textContent = "Akriti Speaking";
        subtitle.textContent = "Speaking back response...";
      } else {
        aura.classList.remove('scale-110', 'animate-spin');
        waveBars.classList.add('hidden');
        micBtn.classList.remove('ring-4', 'ring-indigo-400');
        title.textContent = "Akriti Voice Assistant";
        subtitle.textContent = "Tap the microphone to speak, or type below.";
      }
    }

    function toggleTts() {
      ttsEnabled = !ttsEnabled;
      document.getElementById('tts-label').textContent = 'Voice Output: ' + (ttsEnabled ? 'ON' : 'OFF');
      const icon = document.getElementById('speaker-icon');
      if (ttsEnabled) {
        icon.classList.remove('text-slate-500');
        icon.classList.add('text-indigo-400');
      } else {
        icon.classList.remove('text-indigo-400');
        icon.classList.add('text-slate-500');
        window.speechSynthesis.cancel();
      }
    }

    function speakText(text) {
      if (!ttsEnabled || !('speechSynthesis' in window)) return;
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = currentLanguage;
      utterance.onstart = () => updateVoiceUiState('speaking');
      utterance.onend = () => updateVoiceUiState('idle');
      utterance.onerror = () => updateVoiceUiState('idle');
      window.speechSynthesis.speak(utterance);
    }

    async function sendTextMessage() {
      const input = document.getElementById('text-input');
      const text = input.value.trim();
      if (!text && !attachedImageBase64) return;
      input.value = '';

      // Append user bubble
      appendMessage('user', text || '(Analyzed Image)');
      updateVoiceUiState('thinking');

      try {
        let res;
        if (attachedImageBase64) {
          res = await fetch('/api/vision', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              prompt: text || 'Please describe this image and give key insights.',
              imageBase64: attachedImageBase64,
              mimeType: attachedImageMime
            })
          });
          clearAttachedImage();
        } else {
          // Check for local device command shortcuts
          const lower = text.toLowerCase();
          if (lower.includes('flashlight')) {
            toggleDeviceAction('Flashlight');
          } else if (lower.includes('wi-fi') || lower.includes('wifi')) {
            toggleDeviceAction('Wi-Fi');
          } else if (lower.includes('take a note') || lower.includes('save note')) {
            const noteContent = text.replace(/take a note|save note|note/gi, '').trim() || text;
            addVoiceNote(noteContent);
          }

          res = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text, history: chatHistory })
          });
        }

        const data = await res.json();
        const reply = data.reply || data.error || "No response received.";
        appendMessage('assistant', reply);
        speakText(reply);
      } catch (err) {
        appendMessage('assistant', 'Sorry, I had trouble processing that request.');
        updateVoiceUiState('idle');
      }
    }

    function appendMessage(role, text) {
      chatHistory.push({ role, text });
      const stream = document.getElementById('chat-stream');
      const isUser = role === 'user';

      const div = document.createElement('div');
      div.className = 'flex items-start gap-3 ' + (isUser ? 'flex-row-reverse' : '');

      const avatar = document.createElement('div');
      avatar.className = 'w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold shrink-0 ' + 
        (isUser ? 'bg-purple-600 text-white' : 'bg-indigo-500/20 text-indigo-400');
      avatar.textContent = isUser ? 'You' : 'A';

      const bubble = document.createElement('div');
      bubble.className = 'rounded-2xl p-3 text-sm max-w-[85%] border shadow-sm leading-relaxed ' + 
        (isUser ? 'bg-indigo-600 text-white rounded-tr-none border-indigo-500' : 'bg-slate-800/90 text-slate-200 rounded-tl-none border-slate-700/60');
      bubble.textContent = text;

      div.appendChild(avatar);
      div.appendChild(bubble);
      stream.appendChild(div);
      stream.scrollTop = stream.scrollHeight;
    }

    function clearChat() {
      chatHistory = [];
      document.getElementById('chat-stream').innerHTML = '';
      appendMessage('assistant', "Chat cleared. Ready for your voice questions!");
    }

    // Image handling
    function handleImageSelected(e) {
      const file = e.target.files[0];
      if (!file) return;
      attachedImageMime = file.type;
      const reader = new FileReader();
      reader.onload = (evt) => {
        const base64 = evt.target.result.split(',')[1];
        attachedImageBase64 = base64;
        document.getElementById('image-preview-bar').classList.remove('hidden');
        document.getElementById('image-preview-name').textContent = file.name;
        document.getElementById('text-input').placeholder = "Ask Akriti a question about this image...";
      };
      reader.readAsDataURL(file);
    }

    function clearAttachedImage() {
      attachedImageBase64 = null;
      attachedImageMime = null;
      document.getElementById('image-input').value = '';
      document.getElementById('image-preview-bar').classList.add('hidden');
      document.getElementById('text-input').placeholder = "Type a message or command...";
    }

    // Phone Actions Simulation
    const deviceStates = {
      Flashlight: false,
      'Wi-Fi': true,
      'Battery Saver': false
    };

    function toggleDeviceAction(action) {
      deviceStates[action] = !deviceStates[action];
      if (action === 'Flashlight') {
        const elem = document.getElementById('state-flashlight');
        elem.textContent = deviceStates[action] ? 'ON (Active)' : 'Off';
        elem.className = deviceStates[action] ? 'text-[11px] text-amber-400 font-semibold' : 'text-[11px] text-slate-400';
      } else if (action === 'Wi-Fi') {
        const elem = document.getElementById('state-wifi');
        elem.textContent = deviceStates[action] ? 'Connected' : 'Disabled';
        elem.className = deviceStates[action] ? 'text-[11px] text-emerald-400' : 'text-[11px] text-slate-500';
      } else if (action === 'Battery Saver') {
        const elem = document.getElementById('state-battery');
        elem.textContent = deviceStates[action] ? 'Enabled (Eco)' : '84% Normal';
        elem.className = deviceStates[action] ? 'text-[11px] text-amber-400' : 'text-[11px] text-slate-400';
      }
      speakText(action + ' is now ' + (deviceStates[action] ? 'on' : 'off'));
    }

    function simulateAlarm() {
      const now = new Date();
      now.setHours(now.getHours() + 1);
      const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      document.getElementById('state-alarm').textContent = timeStr;
      speakText('Alarm scheduled for ' + timeStr);
      appendMessage('assistant', 'Alarm set for ' + timeStr + ' on your Android device.');
    }

    // Notes management
    async function loadNotes() {
      try {
        const res = await fetch('/api/notes');
        const data = await res.json();
        renderNotes(data.notes || []);
      } catch (e) {
        console.error(e);
      }
    }

    function renderNotes(notes) {
      const list = document.getElementById('notes-list');
      list.innerHTML = '';
      if (notes.length === 0) {
        list.innerHTML = '<div class="text-xs text-slate-500 py-3 text-center">No voice notes yet. Say "Take a note..." to create one.</div>';
        return;
      }
      notes.forEach(note => {
        const div = document.createElement('div');
        div.className = 'p-2.5 rounded-xl bg-slate-950/80 border border-slate-800 flex items-start justify-between gap-2 text-xs group';
        div.innerHTML = \`
          <div class="flex-1">
            <p class="text-slate-200 leading-snug">\${escapeHtml(note.text)}</p>
            <span class="text-[10px] text-slate-500">\${escapeHtml(note.timestamp)}</span>
          </div>
          <button onclick="deleteNote('\${note.id}')" class="text-slate-500 hover:text-red-400 transition p-1" title="Delete note">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
          </button>
        \`;
        list.appendChild(div);
      });
    }

    async function addVoiceNote(text) {
      try {
        const res = await fetch('/api/notes', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text })
        });
        const data = await res.json();
        renderNotes(data.notes || []);
      } catch (e) {
        console.error(e);
      }
    }

    function addVoiceNotePrompt() {
      const text = prompt('Enter a voice note:');
      if (text && text.trim()) {
        addVoiceNote(text.trim());
      }
    }

    async function deleteNote(id) {
      try {
        const res = await fetch('/api/notes?id=' + encodeURIComponent(id), { method: 'DELETE' });
        const data = await res.json();
        renderNotes(data.notes || []);
      } catch (e) {
        console.error(e);
      }
    }

    function escapeHtml(str) {
      return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Initial load
    loadNotes();
  </script>
</body>
</html>`;
}

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Akriti Dev Server listening on port ${PORT}`);
});
