package com.example.util

object SsiWebClientHtml {

    fun getHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>SSI - Imported Slideshow</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; -webkit-user-select: none; }
    body {
      background: #080d1a;
      color: #e2e8f0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
    }

    button { font-family: inherit; outline: none; border: none; }

    /* ============================================================
       1. PIN AUTHENTICATION SCREEN
       ============================================================ */
    #pin-screen {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      text-align: center;
    }
    .card {
      background: #0f172a;
      border: 1px solid #1e293b;
      border-radius: 20px;
      padding: 32px 24px;
      max-width: 360px;
      width: 100%;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5);
    }
    .logo-badge { font-size: 38px; margin-bottom: 12px; }
    h1 { font-size: 20px; font-weight: 700; color: #10b981; margin-bottom: 6px; }
    p.subtitle { font-size: 13px; color: #94a3b8; margin-bottom: 24px; }
    .pin-dots {
      display: flex;
      gap: 16px;
      justify-content: center;
      margin-bottom: 24px;
    }
    .dot {
      width: 16px;
      height: 16px;
      border-radius: 50%;
      border: 2px solid #10b981;
      background: transparent;
      transition: all 0.15s ease;
    }
    .dot.filled {
      background: #10b981;
      box-shadow: 0 0 10px rgba(16, 185, 129, 0.7);
    }
    .keypad {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }
    .key-btn {
      background: #1e293b;
      color: #f8fafc;
      border: 1px solid #334155;
      border-radius: 14px;
      font-size: 22px;
      font-weight: 600;
      height: 56px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.1s;
    }
    .key-btn:active { background: #10b981; color: #0f172a; transform: scale(0.96); }
    .key-action { font-size: 14px; font-weight: 700; color: #94a3b8; }
    #pin-error {
      color: #f43f5e;
      font-size: 12px;
      font-weight: 600;
      min-height: 18px;
      margin-top: 8px;
    }
    .security-note {
      margin-top: 20px;
      font-size: 11px;
      color: #64748b;
      line-height: 1.4;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }

    /* ============================================================
       2. IMAGE CATALOG SCREEN (100% Matching In-App SSI Tab)
       ============================================================ */
    #catalog-screen {
      flex: 1;
      display: flex;
      flex-direction: column;
      width: 100%;
      max-width: 900px;
      margin: 0 auto;
      background: #080d1a;
      min-height: 100vh;
    }

    .ssi-header {
      background: #0f172a;
      border-bottom: 1px solid #1e293b;
      padding: 14px 16px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      position: sticky;
      top: 0;
      z-index: 30;
      box-shadow: 0 4px 16px rgba(0,0,0,0.3);
    }
    .header-top-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .title-group {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }
    .app-badge {
      font-size: 17px;
      font-weight: 700;
      color: #10b981;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .stats-text {
      font-size: 11px;
      color: #94a3b8;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .icon-btn {
      background: #1e293b;
      color: #10b981;
      border: 1px solid #334155;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      cursor: pointer;
      transition: all 0.15s;
    }
    .icon-btn:active { transform: scale(0.92); background: #10b981; color: #000; }
    .icon-btn.spinning { animation: spin 0.8s linear infinite; }
    @keyframes spin { 100% { transform: rotate(360deg); } }

    .actions-row {
      display: flex;
      gap: 10px;
    }
    .slideshow-launch-btn {
      flex: 1;
      height: 46px;
      background: #10b981;
      color: #000000;
      border-radius: 12px;
      font-size: 15px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
      transition: transform 0.1s, background-color 0.15s;
    }
    .slideshow-launch-btn:active { transform: scale(0.98); background: #059669; }
    .slideshow-launch-btn:disabled {
      background: rgba(16, 185, 129, 0.35);
      color: rgba(0, 0, 0, 0.4);
      cursor: not-allowed;
      box-shadow: none;
    }

    .interval-card {
      background: rgba(30, 41, 59, 0.5);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      padding: 10px 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .interval-top-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .interval-label-group {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
    }
    .timer-icon { color: #10b981; font-size: 14px; }
    .interval-label-text { font-weight: 500; color: #f8fafc; }
    .interval-value-text { font-weight: 700; color: #10b981; font-size: 13px; }

    .loop-toggle-group {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #94a3b8;
    }
    .switch {
      position: relative;
      display: inline-block;
      width: 38px;
      height: 22px;
    }
    .switch input { opacity: 0; width: 0; height: 0; }
    .slider {
      position: absolute;
      cursor: pointer;
      top: 0; left: 0; right: 0; bottom: 0;
      background-color: #334155;
      transition: .2s;
      border-radius: 22px;
    }
    .slider:before {
      position: absolute;
      content: "";
      height: 16px;
      width: 16px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .2s;
      border-radius: 50%;
    }
    input:checked + .slider { background-color: #10b981; }
    input:checked + .slider:before { transform: translateX(16px); }

    .slider-container {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .range-slider {
      -webkit-appearance: none;
      width: 100%;
      height: 6px;
      border-radius: 3px;
      background: #1e293b;
      outline: none;
    }
    .range-slider::-webkit-slider-thumb {
      -webkit-appearance: none;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: #10b981;
      cursor: pointer;
      box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
    }
    .slider-ticks {
      display: flex;
      justify-content: space-between;
      font-size: 10px;
      color: #64748b;
      padding: 0 2px;
    }

    .catalog-main {
      flex: 1;
      padding: 12px 14px 40px;
      display: flex;
      flex-direction: column;
    }
    .center-box {
      margin: auto;
      text-align: center;
      padding: 40px 20px;
    }
    .spinner {
      width: 36px;
      height: 36px;
      border: 3px solid rgba(16, 185, 129, 0.2);
      border-top-color: #10b981;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin: 0 auto;
    }
    .empty-icon { font-size: 48px; margin-bottom: 12px; }
    .empty-title { font-size: 16px; font-weight: 700; color: #f8fafc; margin-bottom: 4px; }
    .empty-desc { font-size: 12px; color: #94a3b8; }

    .image-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(105px, 1fr));
      gap: 10px;
      width: 100%;
    }
    @media (min-width: 600px) {
      .image-grid {
        grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
        gap: 12px;
      }
    }
    @media (min-width: 900px) {
      .image-grid {
        grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
        gap: 14px;
      }
    }

    /* Thumbnail Cards with Canvas Rendering */
    .thumb-card {
      position: relative;
      aspect-ratio: 1;
      border-radius: 10px;
      overflow: hidden;
      background: #111827;
      border: 1px solid rgba(255, 255, 255, 0.1);
      cursor: pointer;
      transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .thumb-card:hover {
      transform: scale(1.03);
      border-color: #10b981;
      box-shadow: 0 4px 14px rgba(16, 185, 129, 0.2);
    }
    .thumb-card:active {
      transform: scale(0.97);
    }
    .thumb-canvas {
      width: 100%;
      height: 100%;
      display: block;
      pointer-events: none;
    }
    .thumb-index-badge {
      position: absolute;
      top: 6px;
      left: 6px;
      background: rgba(0, 0, 0, 0.65);
      color: #ffffff;
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 6px;
      backdrop-filter: blur(4px);
      border: 1px solid rgba(255,255,255,0.15);
    }
    .thumb-name-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      padding: 16px 6px 4px;
      background: linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.85) 100%);
      font-size: 10px;
      color: #e2e8f0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      text-align: center;
    }

    /* ============================================================
       3. FULLSCREEN SLIDESHOW PLAYER (Clean Canvas + Small Exit Button)
       ============================================================ */
    #slideshow-screen {
      display: none;
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: #000000;
      z-index: 100;
      overflow: hidden;
      cursor: pointer;
    }

    /* Small Minimal Exit Button (Top-Right) */
    .small-exit-btn {
      position: absolute;
      top: 16px;
      right: 16px;
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background: rgba(0, 0, 0, 0.55);
      color: #f8fafc;
      border: 1px solid rgba(255, 255, 255, 0.3);
      font-size: 16px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      z-index: 130;
      backdrop-filter: blur(8px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
      transition: transform 0.15s, background-color 0.15s;
    }
    .small-exit-btn:hover {
      background: #10b981;
      color: #000000;
      border-color: #10b981;
    }
    .small-exit-btn:active {
      transform: scale(0.9);
    }

    /* Stage Area & Canvas */
    .stage {
      position: absolute;
      top: 0; left: 0; right: 0; bottom: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      width: 100%;
      height: 100%;
    }
    .slide-canvas {
      width: 100%;
      height: 100%;
      display: block;
      pointer-events: none;
    }

    /* Subtle Floating Toast when Tapped to Stop/Resume */
    .play-state-toast {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%) scale(0.9);
      background: rgba(0, 0, 0, 0.78);
      color: #ffffff;
      padding: 10px 22px;
      border-radius: 30px;
      font-size: 15px;
      font-weight: 700;
      border: 1px solid rgba(255, 255, 255, 0.25);
      backdrop-filter: blur(8px);
      pointer-events: none;
      opacity: 0;
      transition: opacity 0.2s ease, transform 0.2s ease;
      z-index: 120;
    }
    .play-state-toast.show {
      opacity: 1;
      transform: translate(-50%, -50%) scale(1);
    }

    /* ============================================================
       4. TERMINATED SCREEN (Zero-Trace)
       ============================================================ */
    #terminated-screen {
      display: none;
      flex: 1;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      text-align: center;
    }
    .term-icon { font-size: 54px; margin-bottom: 16px; }
    #terminated-screen h2 { color: #f43f5e; margin-bottom: 8px; font-size: 22px; }
    #terminated-screen p { color: #94a3b8; max-width: 320px; line-height: 1.5; font-size: 13px; }
  </style>
</head>
<body oncontextmenu="return false;">

  <!-- PIN Authentication Screen -->
  <div id="pin-screen">
    <div class="card">
      <div class="logo-badge">🔐</div>
      <h1>Enter Host PIN</h1>
      <p class="subtitle">Enter the 4-digit PIN displayed on the host device.</p>

      <div class="pin-dots">
        <div class="dot" id="dot-0"></div>
        <div class="dot" id="dot-1"></div>
        <div class="dot" id="dot-2"></div>
        <div class="dot" id="dot-3"></div>
      </div>

      <div class="keypad">
        <button class="key-btn" onclick="pressDigit('1')">1</button>
        <button class="key-btn" onclick="pressDigit('2')">2</button>
        <button class="key-btn" onclick="pressDigit('3')">3</button>
        <button class="key-btn" onclick="pressDigit('4')">4</button>
        <button class="key-btn" onclick="pressDigit('5')">5</button>
        <button class="key-btn" onclick="pressDigit('6')">6</button>
        <button class="key-btn" onclick="pressDigit('7')">7</button>
        <button class="key-btn" onclick="pressDigit('8')">8</button>
        <button class="key-btn" onclick="pressDigit('9')">9</button>
        <button class="key-btn key-action" onclick="clearPin()">✕</button>
        <button class="key-btn" onclick="pressDigit('0')">0</button>
        <button class="key-btn key-action" onclick="deleteDigit()">⌫</button>
      </div>

      <div id="pin-error"></div>

      <div class="security-note">
        <span>🛡️ Zero-Trace Encrypted Stream (Decrypted in volatile RAM)</span>
      </div>
    </div>
  </div>

  <!-- Image Catalog Screen (100% Identical to In-App SSI Tab) -->
  <div id="catalog-screen" style="display:none;">
    <!-- Top Header Banner & Stats -->
    <header class="ssi-header">
      <div class="header-top-row">
        <div class="title-group">
          <div class="app-badge">📸 SSI (Imported Slideshow)</div>
          <div class="stats-text" id="stats-text">Path: media_imported • 0 images (0 B)</div>
        </div>
        <button class="icon-btn" id="refresh-btn" onclick="refreshCatalog(true)" title="Refresh Images">
          🔄
        </button>
      </div>

      <!-- Slideshow Button (Matches in-app green AccentGreen button) -->
      <div class="actions-row">
        <button class="slideshow-launch-btn" id="launch-slideshow-btn" onclick="startSlideshowFromCatalog(0)">
          <span>▶</span>
          <span>Slideshow</span>
        </button>
      </div>

      <!-- Slideshow Time Interval & Loop Setting Bar (1s to 5s) -->
      <div class="interval-card">
        <div class="interval-top-row">
          <div class="interval-label-group">
            <span class="timer-icon">⏱</span>
            <span class="interval-label-text">Slideshow Interval: </span>
            <span class="interval-value-text" id="catalog-interval-text">2.0s</span>
          </div>
          <div class="loop-toggle-group">
            <span>Loop</span>
            <label class="switch">
              <input type="checkbox" id="catalog-loop-switch" checked onchange="toggleLoopSetting(this.checked)">
              <span class="slider"></span>
            </label>
          </div>
        </div>
        <div class="slider-container">
          <input type="range" min="1.0" max="5.0" step="0.5" value="2.0" class="range-slider" id="catalog-interval-slider" oninput="onIntervalSliderChange(this.value)">
          <div class="slider-ticks">
            <span>1.0s</span>
            <span>2.0s</span>
            <span>3.0s</span>
            <span>4.0s</span>
            <span>5.0s</span>
          </div>
        </div>
      </div>
    </header>

    <!-- Main Images Catalog Grid -->
    <main class="catalog-main">
      <div id="catalog-loading" class="center-box" style="display:none;">
        <div class="spinner"></div>
        <div style="margin-top:12px; color:#94a3b8; font-size:13px;">Decrypting encrypted streams…</div>
      </div>

      <div id="catalog-empty" class="center-box" style="display:none;">
        <div class="empty-icon">📂</div>
        <div class="empty-title">No Imported Images Found</div>
        <div class="empty-desc">Import images in the app's Vault tab using "Import to SSI".</div>
      </div>

      <div id="catalog-grid" class="image-grid">
        <!-- Dynamically populated canvas thumbnail cards -->
      </div>
    </main>
  </div>

  <!-- Fullscreen Slideshow Player (Immersive: Only Small Exit Button) -->
  <div id="slideshow-screen" style="display:none;">
    <!-- Small Minimal Exit Button -->
    <button class="small-exit-btn" id="small-exit-btn" onclick="exitSlideshowToCatalog(event)" title="Exit Slideshow">✕</button>

    <!-- Center Stage with Direct Canvas Rendering -->
    <div class="stage" id="stage">
      <canvas id="main-canvas" class="slide-canvas"></canvas>
    </div>

    <!-- Temporary Status Indicator (shows briefly when tapped to stop/resume) -->
    <div id="play-state-toast" class="play-state-toast">⏸ Stopped</div>
  </div>

  <!-- Terminated Screen -->
  <div id="terminated-screen" style="display:none;">
    <div class="term-icon">🛑</div>
    <h2>Session Terminated</h2>
    <p>The host turned off the session. All photos have been permanently wiped from memory.</p>
  </div>

  <script>
    // Prevent console and extension canvas extraction
    try {
      HTMLCanvasElement.prototype.toDataURL = function() { return ""; };
      HTMLCanvasElement.prototype.toBlob = function() { return null; };
    } catch (_) {}

    let enteredPin = "";
    let authToken = "";
    let imageList = [];
    let currentIndex = 0;
    let isPlaying = true;
    let isAutoLoop = true;
    let slideIntervalSec = 2.0;
    let slideTimer = null;
    let toastTimer = null;

    /* ----------------------------------------------------
       RC4 STREAM CIPHER DECRYPTOR (Pure JS TypedArray)
       ---------------------------------------------------- */
    function rc4(data, key) {
      const s = new Uint8Array(256);
      for (let i = 0; i < 256; i++) s[i] = i;
      let j = 0;
      for (let i = 0; i < 256; i++) {
        j = (j + s[i] + key[i % key.length]) & 0xff;
        const tmp = s[i];
        s[i] = s[j];
        s[j] = tmp;
      }
      let i = 0;
      j = 0;
      const out = new Uint8Array(data.length);
      for (let k = 0; k < data.length; k++) {
        i = (i + 1) & 0xff;
        j = (j + s[i]) & 0xff;
        const tmp = s[i];
        s[i] = s[j];
        s[j] = tmp;
        const kByte = s[(s[i] + s[j]) & 0xff];
        out[k] = data[k] ^ kByte;
      }
      return out;
    }

    async function fetchAndDecryptImage(imageId) {
      const res = await fetch("/api/image/" + imageId + "?token=" + encodeURIComponent(authToken), {
        cache: "no-store"
      });
      if (!res.ok) throw new Error("Failed to load encrypted stream");
      const mimeType = res.headers.get("x-image-mime") || "image/jpeg";
      const rawBuffer = await res.arrayBuffer();
      if (rawBuffer.byteLength < 9) throw new Error("Invalid payload");

      const ivBytes = new Uint8Array(rawBuffer, 0, 8);
      const cipherBytes = new Uint8Array(rawBuffer, 8);
      let ivHex = "";
      for (let b = 0; b < 8; b++) {
        ivHex += ("0" + ivBytes[b].toString(16)).slice(-2);
      }

      const keyString = authToken + ":" + enteredPin + ":" + ivHex;
      const keyBytes = new TextEncoder().encode(keyString);
      const plainBytes = rc4(cipherBytes, keyBytes);

      const blob = new Blob([plainBytes], { type: mimeType });
      plainBytes.fill(0); // Instantly wipe raw bytes from memory buffer

      if (window.createImageBitmap) {
        return await createImageBitmap(blob);
      } else {
        return new Promise((resolve, reject) => {
          const objUrl = URL.createObjectURL(blob);
          const tempImg = new Image();
          tempImg.onload = () => {
            URL.revokeObjectURL(objUrl);
            resolve(tempImg);
          };
          tempImg.onerror = (e) => {
            URL.revokeObjectURL(objUrl);
            reject(e);
          };
          tempImg.src = objUrl;
        });
      }
    }

    function drawToCover(canvas, bitmap) {
      const ctx = canvas.getContext("2d");
      const cw = canvas.width;
      const ch = canvas.height;
      const bw = bitmap.width;
      const bh = bitmap.height;
      const cr = cw / ch;
      const br = bw / bh;
      let sx, sy, sw, sh;
      if (br > cr) {
        sh = bh;
        sw = bh * cr;
        sx = (bw - sw) / 2;
        sy = 0;
      } else {
        sw = bw;
        sh = bw / cr;
        sx = 0;
        sy = (bh - sh) / 2;
      }
      ctx.drawImage(bitmap, sx, sy, sw, sh, 0, 0, cw, ch);
    }

    function drawToContain(canvas, bitmap) {
      const ctx = canvas.getContext("2d");
      const cw = canvas.width;
      const ch = canvas.height;
      ctx.clearRect(0, 0, cw, ch);
      const bw = bitmap.width;
      const bh = bitmap.height;
      const scale = Math.min(cw / bw, ch / bh);
      const dw = bw * scale;
      const dh = bh * scale;
      const dx = (cw - dw) / 2;
      const dy = (ch - dh) / 2;
      ctx.drawImage(bitmap, dx, dy, dw, dh);
    }

    function purgeMemory() {
      sessionStorage.clear();
      localStorage.clear();
      const mainCanvas = document.getElementById("main-canvas");
      if (mainCanvas) {
        const ctx = mainCanvas.getContext("2d");
        if (ctx) ctx.clearRect(0, 0, mainCanvas.width, mainCanvas.height);
      }
      const grid = document.getElementById("catalog-grid");
      if (grid) grid.innerHTML = "";
      imageList = [];
    }
    window.addEventListener("pagehide", purgeMemory);
    window.addEventListener("beforeunload", purgeMemory);

    function pressDigit(d) {
      if (enteredPin.length < 4) {
        enteredPin += d;
        updateDots();
        if (enteredPin.length === 4) {
          submitPin();
        }
      }
    }

    function deleteDigit() {
      if (enteredPin.length > 0) {
        enteredPin = enteredPin.slice(0, -1);
        updateDots();
        document.getElementById("pin-error").innerText = "";
      }
    }

    function clearPin() {
      enteredPin = "";
      updateDots();
      document.getElementById("pin-error").innerText = "";
    }

    function updateDots() {
      for (let i = 0; i < 4; i++) {
        const dot = document.getElementById("dot-" + i);
        if (i < enteredPin.length) {
          dot.classList.add("filled");
        } else {
          dot.classList.remove("filled");
        }
      }
    }

    /* ----------------------------------------------------
       KEYBOARD CONTROLS:
       - Space: Stop / Resume
       - Left Arrow: Previous Image
       - Right Arrow: Next Image
       - Escape: Exit Fullscreen Slideshow
       ---------------------------------------------------- */
    window.addEventListener("keydown", (e) => {
      if (document.getElementById("pin-screen").style.display === "none") {
        if (document.getElementById("slideshow-screen").style.display === "block" ||
            document.getElementById("slideshow-screen").style.display === "flex") {
          if (e.code === "Space" || e.key === " ") {
            e.preventDefault();
            togglePlay();
          } else if (e.key === "ArrowRight" || e.key === "ArrowDown") {
            e.preventDefault();
            nextSlide();
          } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
            e.preventDefault();
            prevSlide();
          } else if (e.key === "Escape") {
            e.preventDefault();
            exitSlideshowToCatalog();
          }
        }
        return;
      }
      if (e.key >= "0" && e.key <= "9") {
        pressDigit(e.key);
      } else if (e.key === "Backspace") {
        deleteDigit();
      } else if (e.key === "Escape") {
        clearPin();
      }
    });

    async function submitPin() {
      document.getElementById("pin-error").innerText = "Verifying…";
      try {
        const resp = await fetch("/api/verify", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ pin: enteredPin })
        });
        const data = await resp.json();
        if (resp.ok && data.success) {
          authToken = data.token;
          document.getElementById("pin-screen").style.display = "none";
          document.getElementById("catalog-screen").style.display = "flex";
          await loadCatalog();
          startHeartbeat();
        } else {
          document.getElementById("pin-error").innerText = data.error || "Incorrect 4-digit PIN";
          enteredPin = "";
          updateDots();
        }
      } catch (err) {
        document.getElementById("pin-error").innerText = "Connection error. Ensure device is on same network.";
        enteredPin = "";
        updateDots();
      }
    }

    async function loadCatalog() {
      const loading = document.getElementById("catalog-loading");
      const empty = document.getElementById("catalog-empty");
      const grid = document.getElementById("catalog-grid");
      const launchBtn = document.getElementById("launch-slideshow-btn");

      loading.style.display = "block";
      empty.style.display = "none";
      grid.innerHTML = "";

      try {
        const resp = await fetch("/api/images?token=" + encodeURIComponent(authToken), { cache: "no-store" });
        if (!resp.ok) throw new Error("Unauthorized");
        const data = await resp.json();
        imageList = data.images || [];

        loading.style.display = "none";

        if (imageList.length === 0) {
          empty.style.display = "block";
          launchBtn.disabled = true;
          document.getElementById("stats-text").innerText = "Path: media_imported • 0 images (0 B)";
          return;
        }

        launchBtn.disabled = false;

        let totalBytes = 0;
        for (let i = 0; i < imageList.length; i++) {
          totalBytes += (imageList[i].sizeBytes || 0);
        }
        const formattedSize = formatBytes(totalBytes);
        document.getElementById("stats-text").innerText = "Path: media_imported • " + imageList.length + " images (" + formattedSize + ")";

        renderGrid();
      } catch (e) {
        loading.style.display = "none";
        alert("Failed to load images from host: " + e.message);
      }
    }

    function formatBytes(bytes) {
      if (!bytes || bytes <= 0) return "0 B";
      const kb = bytes / 1024.0;
      const mb = kb / 1024.0;
      if (mb >= 1.0) {
        return mb.toFixed(2) + " MB";
      } else {
        return kb.toFixed(1) + " KB";
      }
    }

    function renderGrid() {
      const grid = document.getElementById("catalog-grid");
      grid.innerHTML = "";

      for (let i = 0; i < imageList.length; i++) {
        const item = imageList[i];
        const card = document.createElement("div");
        card.className = "thumb-card";
        card.title = item.name + " (Tap to play slideshow from here)";

        const canvas = document.createElement("canvas");
        canvas.className = "thumb-canvas";
        canvas.width = 220;
        canvas.height = 220;

        const badge = document.createElement("div");
        badge.className = "thumb-index-badge";
        badge.innerText = "#" + (i + 1);

        const nameBar = document.createElement("div");
        nameBar.className = "thumb-name-bar";
        nameBar.innerText = item.name;

        card.appendChild(canvas);
        card.appendChild(badge);
        card.appendChild(nameBar);

        const cardIndex = i;
        card.onclick = function() {
          startSlideshowFromCatalog(cardIndex);
        };

        grid.appendChild(card);

        // Asynchronously load and decrypt thumbnail
        loadThumbCanvas(canvas, item.id);
      }
    }

    async function loadThumbCanvas(canvas, imageId) {
      try {
        const bitmap = await fetchAndDecryptImage(imageId);
        drawToCover(canvas, bitmap);
        if (bitmap.close) bitmap.close();
      } catch (_) {}
    }

    async function refreshCatalog(showAnim) {
      const btn = document.getElementById("refresh-btn");
      if (showAnim) btn.classList.add("spinning");
      await loadCatalog();
      if (showAnim) setTimeout(() => btn.classList.remove("spinning"), 600);
    }

    function onIntervalSliderChange(val) {
      slideIntervalSec = parseFloat(val);
      document.getElementById("catalog-interval-text").innerText = slideIntervalSec.toFixed(1) + "s";
      if (isPlaying && document.getElementById("slideshow-screen").style.display !== "none") {
        startSlideshowTimer();
      }
    }

    function toggleLoopSetting(checked) {
      isAutoLoop = checked;
    }

    /* ----------------------------------------------------
       FULLSCREEN SLIDESHOW LOGIC:
       - Direct Canvas Rendering (No <img> in DOM)
       - Only small exit button shown
       - Left / Right Swipe
       - Left / Right Arrow keys
       - Space to Stop / Play
       - One Tap to Stop / Play
       ---------------------------------------------------- */
    function startSlideshowFromCatalog(startIndex) {
      if (imageList.length === 0) return;
      currentIndex = Math.max(0, Math.min(startIndex, imageList.length - 1));

      document.getElementById("catalog-screen").style.display = "none";
      const s = document.getElementById("slideshow-screen");
      s.style.display = "block";

      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(() => {});
      }

      isPlaying = true;
      showSlide(currentIndex);
      startSlideshowTimer();
    }

    function exitSlideshowToCatalog(e) {
      if (e) {
        e.stopPropagation();
        e.preventDefault();
      }
      stopSlideshowTimer();

      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
      }

      document.getElementById("slideshow-screen").style.display = "none";
      document.getElementById("catalog-screen").style.display = "flex";
    }

    async function showSlide(index) {
      if (imageList.length === 0) return;
      if (index < 0) index = imageList.length - 1;
      if (index >= imageList.length) {
        if (!isAutoLoop) {
          pauseSlideshow();
          showPlayToast("⏸ Ended");
          return;
        }
        index = 0;
      }
      currentIndex = index;

      try {
        const item = imageList[currentIndex];
        const bitmap = await fetchAndDecryptImage(item.id);
        const canvas = document.getElementById("main-canvas");
        const dpr = window.devicePixelRatio || 1;
        canvas.width = window.innerWidth * dpr;
        canvas.height = window.innerHeight * dpr;
        drawToContain(canvas, bitmap);
        if (bitmap.close) bitmap.close();
      } catch (err) {
        // Disconnected or error
      }
    }

    function nextSlide() {
      if (imageList.length === 0) return;
      showSlide(currentIndex + 1);
      if (isPlaying) startSlideshowTimer();
    }

    function prevSlide() {
      if (imageList.length === 0) return;
      showSlide(currentIndex - 1);
      if (isPlaying) startSlideshowTimer();
    }

    function togglePlay() {
      if (isPlaying) {
        pauseSlideshow();
        showPlayToast("⏸ Stopped");
      } else {
        resumeSlideshow();
        showPlayToast("▶ Playing");
      }
    }

    function pauseSlideshow() {
      isPlaying = false;
      stopSlideshowTimer();
    }

    function resumeSlideshow() {
      isPlaying = true;
      startSlideshowTimer();
    }

    function showPlayToast(msg) {
      const toast = document.getElementById("play-state-toast");
      if (!toast) return;
      toast.innerText = msg;
      toast.classList.add("show");
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => {
        toast.classList.remove("show");
      }, 800);
    }

    function startSlideshowTimer() {
      stopSlideshowTimer();
      const speedMs = Math.round(slideIntervalSec * 1000);
      slideTimer = setTimeout(() => {
        nextSlide();
      }, speedMs);
    }

    function stopSlideshowTimer() {
      if (slideTimer) clearTimeout(slideTimer);
      slideTimer = null;
    }

    /* ----------------------------------------------------
       TOUCH & TAP GESTURES (SWIPE LEFT/RIGHT & ONE TAP TO STOP)
       ---------------------------------------------------- */
    let touchStartX = 0;
    let touchStartY = 0;
    let touchEndX = 0;
    let touchEndY = 0;
    let touchMoved = false;
    let lastTouchEndTime = 0;

    const stageEl = document.getElementById("stage");

    stageEl.addEventListener("touchstart", (e) => {
      if (e.touches.length > 0) {
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
        touchEndX = touchStartX;
        touchEndY = touchStartY;
        touchMoved = false;
      }
    }, { passive: true });

    stageEl.addEventListener("touchmove", (e) => {
      if (e.touches.length > 0) {
        touchEndX = e.touches[0].clientX;
        touchEndY = e.touches[0].clientY;
        const dx = Math.abs(touchEndX - touchStartX);
        const dy = Math.abs(touchEndY - touchStartY);
        if (dx > 12 || dy > 12) {
          touchMoved = true;
        }
      }
    }, { passive: true });

    stageEl.addEventListener("touchend", (e) => {
      lastTouchEndTime = Date.now();
      const dx = touchEndX - touchStartX;
      const dy = touchEndY - touchStartY;

      if (Math.abs(dx) > 35 && Math.abs(dx) > Math.abs(dy)) {
        if (dx < 0) {
          nextSlide();
        } else {
          prevSlide();
        }
      } else if (!touchMoved) {
        // ONE TAP ON PHONE TO STOP / RESUME
        togglePlay();
      }
    }, { passive: true });

    // Desktop/Laptop mouse click: ONE TAP TO STOP / RESUME
    stageEl.addEventListener("click", (e) => {
      if (Date.now() - lastTouchEndTime < 400) return;
      togglePlay();
    });

    window.addEventListener("resize", () => {
      if (document.getElementById("slideshow-screen").style.display === "block") {
        showSlide(currentIndex);
      }
    });

    /* ----------------------------------------------------
       HEARTBEAT & TERMINATION
       ---------------------------------------------------- */
    function startHeartbeat() {
      setInterval(async () => {
        try {
          const res = await fetch("/api/status", { cache: "no-store" });
          if (!res.ok) throw new Error("Server down");
          const data = await res.json();
          if (!data.hosting) {
            terminateClient();
          }
        } catch (_) {
          terminateClient();
        }
      }, 2000);
    }

    function terminateClient() {
      purgeMemory();
      document.getElementById("slideshow-screen").style.display = "none";
      document.getElementById("catalog-screen").style.display = "none";
      document.getElementById("pin-screen").style.display = "none";
      document.getElementById("terminated-screen").style.display = "flex";
    }
  </script>
</body>
</html>
        """.trimIndent()
    }
}
