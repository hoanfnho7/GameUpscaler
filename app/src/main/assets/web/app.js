// Antigravity GameUpscaler Web App Bridge & Interactions
(function() {
  'use strict';

  let currentTab = 0;
  let devicePhysW = 1080;
  let devicePhysH = 2400;
  let selectedFactor = 1.15;
  let selectedGamePkg = null;
  let gamesList = [];
  let isOverlayActive = false;

  // Clock Update
  function updateClock() {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    const el = document.getElementById('clock-display');
    if (el) el.textContent = `${h}:${m}`;
  }
  setInterval(updateClock, 1000);
  updateClock();

  // Tab Navigation
  window.switchTab = function(idx) {
    currentTab = idx;
    const tabIds = ['tab-home', 'tab-ssaa', 'tab-hud'];
    tabIds.forEach((id, i) => {
      const el = document.getElementById(id);
      if (el) {
        if (i === idx) el.classList.remove('hidden');
        else el.classList.add('hidden');
      }
    });

    const navBtns = document.querySelectorAll('.nav-btn');
    navBtns.forEach((btn, i) => {
      if (i === idx) {
        btn.className = 'nav-btn flex-1 max-w-[104px] py-1.5 px-space-xs rounded-full flex flex-col items-center justify-center gap-0.5 bg-primary-container/20 text-primary-fixed ring-1 ring-primary-container/40 transition-all duration-200 min-h-[44px]';
      } else {
        btn.className = 'nav-btn flex-1 max-w-[104px] py-1.5 px-space-xs rounded-full flex flex-col items-center justify-center gap-0.5 text-on-surface-variant hover:text-on-surface transition-all duration-200 min-h-[44px]';
      }
    });

    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // SSAA Scale Selection
  function initSsaaOptions() {
    const cards = document.querySelectorAll('.ssaa-card');
    cards.forEach(card => {
      card.addEventListener('click', function() {
        const ratio = parseFloat(this.getAttribute('data-ratio'));
        selectedFactor = ratio;
        if (window.Android && window.Android.setSsaaFactor) {
          window.Android.setSsaaFactor(ratio);
        }
        updateSsaaCardStyles(ratio);
      });
    });
  }

  function updateSsaaCardStyles(activeRatio) {
    const cards = document.querySelectorAll('.ssaa-card');
    cards.forEach(card => {
      const r = parseFloat(card.getAttribute('data-ratio'));
      const isAct = Math.abs(r - activeRatio) < 0.01;
      const radioBox = card.querySelector('.ssaa-radio');
      const radioDot = card.querySelector('.ssaa-dot');
      const titleSpan = card.querySelector('.font-headline-sm');

      if (isAct) {
        card.className = 'ssaa-card active-card cursor-pointer rounded-lg bg-surface-container-high p-space-md transition-all active:scale-[0.99] relative overflow-hidden';
        if (radioBox) radioBox.className = 'w-6 h-6 rounded-full bg-primary-container flex items-center justify-center shrink-0 ssaa-radio mt-0.5';
        if (radioDot) radioDot.className = 'w-2.5 h-2.5 rounded-full bg-on-primary-container ssaa-dot';
        if (titleSpan) titleSpan.className = 'font-headline-sm text-headline-sm font-bold text-primary-fixed';
      } else {
        card.className = 'ssaa-card cursor-pointer rounded-lg bg-surface-container p-space-md transition-all active:scale-[0.99] hover:bg-surface-container-high/80';
        if (radioBox) radioBox.className = 'w-6 h-6 rounded-full bg-surface-container-low flex items-center justify-center shrink-0 ssaa-radio mt-0.5';
        if (radioDot) radioDot.className = 'w-2 h-2 rounded-full bg-transparent ssaa-dot';
        if (titleSpan) titleSpan.className = 'font-headline-sm text-headline-sm font-bold text-on-surface';
      }
    });
  }

  function updateResolutionLabels(physW, physH) {
    document.querySelectorAll('.scale-res-text').forEach(el => {
      const r = parseFloat(el.getAttribute('data-ratio'));
      const targetW = Math.round(physW * r);
      const targetH = Math.round(physH * r);
      el.textContent = `${targetW} × ${targetH}`;
    });
  }

  // Global SSAA switch
  const globalToggle = document.getElementById('global-ssaa-toggle');
  if (globalToggle) {
    globalToggle.addEventListener('change', function() {
      if (window.Android && window.Android.setGlobalSsaa) {
        window.Android.setGlobalSsaa(this.checked);
      }
    });
  }

  // Reset Display Button
  window.handleResetDisplay = function() {
    if (window.Android && window.Android.resetDisplay) {
      window.Android.resetDisplay();
    }
  };

  // Optimize SSAA button on Tab 0
  const btnOpt = document.getElementById('btnOptimize');
  if (btnOpt) {
    btnOpt.addEventListener('click', function() {
      switchTab(1);
    });
  }

  // Game Whitelist Rendering
  function renderGamesList(apps) {
    gamesList = apps || [];
    const container = document.getElementById('game-library-list');
    if (!container) return;

    if (gamesList.length === 0) {
      container.innerHTML = '<div class="p-space-md text-center text-on-surface-variant font-body-sm">Không tìm thấy game nào trên thiết bị</div>';
      return;
    }

    let html = '';
    gamesList.forEach((game, idx) => {
      const customScale = game.customScale || 0;
      let scaleLabel = '';
      if (customScale > 0) {
        scaleLabel = `${customScale.toFixed(2)}x SSAA (Khóa riêng)`;
      } else {
        scaleLabel = `${selectedFactor.toFixed(2)}x SSAA (Mặc định)`;
      }

      const isChecked = (game.isEnabled !== false) ? 'checked' : '';
      const borderClass = (idx < gamesList.length - 1) ? 'border-b border-surface-container-highest/60' : '';

      html += `
        <div class="flex items-center justify-between p-space-md hover:bg-surface-container-high/50 transition-colors ${borderClass}">
          <div class="flex items-center gap-space-sm min-w-0 flex-1 mr-2 cursor-pointer" onclick="handleLaunchGame('${game.pkg}')">
            <img src="${game.icon}" class="w-11 h-11 rounded-lg bg-surface-container-highest object-cover shrink-0" alt="${game.name}">
            <div class="flex flex-col min-w-0">
              <span class="font-headline-sm text-headline-sm font-semibold text-on-surface truncate">${game.name}</span>
              <div class="flex items-center gap-2 text-on-surface-variant mt-0.5">
                <span class="font-label-mono text-label-mono text-surface-tint">${scaleLabel}</span>
                <span class="text-[10px]">•</span>
                <span class="font-label-mono text-label-mono text-on-surface-variant truncate">${game.fpsTarget || '120 FPS'}</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-space-sm shrink-0">
            <button class="px-2.5 py-1 rounded-full bg-surface-container-highest hover:bg-surface-bright text-primary-fixed font-label-mono text-label-mono" type="button" onclick="openGameScaleModal('${game.pkg}', '${game.name}', ${customScale})">
              Đổi
            </button>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" class="sr-only peer" ${isChecked} onchange="handleToggleGame('${game.pkg}', this.checked)">
              <div class="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-surface-dim after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-on-primary-fixed after:border-surface-container after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-container"></div>
            </label>
          </div>
        </div>
      `;
    });
    container.innerHTML = html;
  }

  window.handleLaunchGame = function(pkg) {
    if (window.Android && window.Android.launchGame) {
      window.Android.launchGame(pkg);
    }
  };

  window.handleToggleGame = function(pkg, enabled) {
    if (window.Android && window.Android.setGameEnabled) {
      window.Android.setGameEnabled(pkg, enabled);
    }
  };

  // Game Scale Modal
  window.openGameScaleModal = function(pkg, name, currentScale) {
    selectedGamePkg = pkg;
    const modal = document.getElementById('game-scale-modal');
    const nameEl = document.getElementById('modal-game-name');
    const listEl = document.getElementById('modal-options-list');
    if (!modal || !nameEl || !listEl) return;

    nameEl.textContent = name;
    const curVal = currentScale || 0;

    const opts = [
      { val: 0, title: 'Mặc định chung', sub: `Theo tỉ lệ toàn cục (${selectedFactor.toFixed(2)}x)` },
      { val: 1.10, title: 'Khóa 1.10x', sub: 'Nhẹ nhàng, tiết kiệm pin' },
      { val: 1.15, title: 'Khóa 1.15x', sub: 'Cân bằng độ nét và nhiệt độ' },
      { val: 1.20, title: 'Khóa 1.20x', sub: 'Hình ảnh rõ nét, độ phân giải cao' },
      { val: 1.25, title: 'Khóa 1.25x', sub: 'Cực nét chuẩn Esports, tối đa GPU' }
    ];

    let html = '';
    opts.forEach(opt => {
      const checked = (Math.abs(opt.val - curVal) < 0.01) ? 'checked' : '';
      html += `
        <label class="flex items-center justify-between p-3 rounded-xl bg-surface-container cursor-pointer hover:bg-surface-container-high transition-colors">
          <div class="flex flex-col">
            <span class="font-headline-sm text-headline-sm font-semibold text-on-surface">${opt.title}</span>
            <span class="font-body-sm text-body-sm text-on-surface-variant">${opt.sub}</span>
          </div>
          <input type="radio" name="game-scale-opt" value="${opt.val}" ${checked} class="accent-primary-container w-5 h-5">
        </label>
      `;
    });
    listEl.innerHTML = html;
    modal.classList.remove('hidden');
  };

  window.closeGameScaleModal = function() {
    const modal = document.getElementById('game-scale-modal');
    if (modal) modal.classList.add('hidden');
  };

  window.saveGameScaleModal = function() {
    const sel = document.querySelector('input[name="game-scale-opt"]:checked');
    if (!sel || !selectedGamePkg) return;
    const scale = parseFloat(sel.value);
    if (window.Android && window.Android.setGameScale) {
      window.Android.setGameScale(selectedGamePkg, scale);
    }
    // Update local game data & re-render
    const g = gamesList.find(x => x.pkg === selectedGamePkg);
    if (g) g.customScale = scale;
    renderGamesList(gamesList);
    closeGameScaleModal();
  };

  // Tab 3: HUD Overlay Functions
  window.toggleSwitch = function(btn, targetElementId) {
    const isEnabled = btn.getAttribute('data-enabled') === 'true';
    const newState = !isEnabled;
    btn.setAttribute('data-enabled', newState.toString());

    const dot = btn.querySelector('.toggle-dot');
    const targetEl = document.getElementById(targetElementId);

    if (newState) {
      btn.classList.remove('bg-surface-container-highest');
      btn.classList.add('bg-primary-container');
      dot.classList.remove('bg-on-surface-variant', 'translate-x-0');
      dot.classList.add('bg-on-primary-container', 'translate-x-6');
      if (targetEl) targetEl.classList.remove('hidden');
    } else {
      btn.classList.remove('bg-primary-container');
      btn.classList.add('bg-surface-container-highest');
      dot.classList.remove('bg-on-primary-container', 'translate-x-6');
      dot.classList.add('bg-on-surface-variant', 'translate-x-0');
      if (targetEl) targetEl.classList.add('hidden');
    }

    // Map targetElementId to metric key
    const metricMap = {
      'hud-item-fps': 'm_fps',
      'hud-item-temp': 'm_bat_temp',
      'hud-item-battery': 'm_watt',
      'hud-item-gpu': 'm_gpu',
      'hud-item-ram': 'm_ram',
      'hud-item-ping': 'm_ping'
    };
    const key = metricMap[targetElementId];
    if (key && window.Android && window.Android.setHudMetric) {
      window.Android.setHudMetric(key, newState);
    }
  };

  window.updateScale = function(val) {
    const scaleVal = document.getElementById('scale-val');
    if (scaleVal) scaleVal.innerText = val + '%' + (val === '100' ? ' (Chuẩn)' : '');
    const hudInner = document.getElementById('hud-inner');
    if (hudInner) hudInner.style.transform = `scale(${val / 100})`;
    if (window.Android && window.Android.setHudScale) {
      window.Android.setHudScale(parseInt(val, 10));
    }
  };

  window.updateOpacity = function(val) {
    const opacityVal = document.getElementById('opacity-val');
    if (opacityVal) opacityVal.innerText = val + '%';
    const hudInner = document.getElementById('hud-inner');
    if (hudInner) hudInner.style.opacity = (val / 100).toString();
    if (window.Android && window.Android.setHudOpacity) {
      window.Android.setHudOpacity(parseInt(val, 10));
    }
  };

  window.selectLayout = function(btn, mode) {
    document.querySelectorAll('.layout-btn').forEach(b => {
      b.classList.remove('bg-surface-container-high', 'text-primary-fixed', 'shadow-sm');
      b.classList.add('bg-surface-container', 'text-on-surface-variant');
    });
    btn.classList.remove('bg-surface-container', 'text-on-surface-variant');
    btn.classList.add('bg-surface-container-high', 'text-primary-fixed', 'shadow-sm');

    const hudInner = document.getElementById('hud-inner');
    let modeIdx = 0;
    if (mode === 'compact') {
      modeIdx = 1;
      if (hudInner) hudInner.className = 'px-3 py-2 rounded-2xl bg-surface-container-lowest/85 backdrop-blur-md shadow-lg grid grid-cols-2 gap-2 text-xs';
    } else if (mode === 'dot') {
      modeIdx = 2;
      if (hudInner) hudInner.className = 'px-2 py-1 rounded-full bg-surface-container-lowest/90 backdrop-blur-md shadow-lg flex items-center gap-1.5';
    } else {
      modeIdx = 0;
      if (hudInner) hudInner.className = 'px-3 py-1.5 rounded-full bg-surface-container-lowest/85 backdrop-blur-md shadow-lg flex items-center gap-2.5 max-w-full overflow-x-auto no-scrollbar';
    }

    if (window.Android && window.Android.setHudLayoutMode) {
      window.Android.setHudLayoutMode(modeIdx);
    }
  };

  window.changeThemeColor = function(colorHex, btn) {
    document.querySelectorAll('.color-dot').forEach(d => {
      d.innerHTML = '';
      d.classList.remove('scale-110');
    });
    btn.classList.add('scale-110');
    btn.innerHTML = '<span class="w-2 h-2 rounded-full bg-on-primary"></span>';
    
    const fpsText = document.querySelector('#hud-item-fps span:last-child');
    if (fpsText) fpsText.style.color = colorHex;

    const colorDots = Array.from(document.querySelectorAll('.color-dot'));
    const idx = colorDots.indexOf(btn);
    if (idx >= 0 && window.Android && window.Android.setHudColorAccent) {
      window.Android.setHudColorAccent(idx);
    }
  };

  // Draggable HUD Simulation in Tab 3 Preview
  function initDragSimulation() {
    const hudPill = document.getElementById('hud-pill');
    if (!hudPill) return;
    let isDragging = false;
    let startX = 0, startY = 0, initialTranslateX = 0, initialTranslateY = 0;

    hudPill.addEventListener('pointerdown', (e) => {
      isDragging = true;
      hudPill.setPointerCapture(e.pointerId);
      startX = e.clientX - initialTranslateX;
      startY = e.clientY - initialTranslateY;
      hudPill.classList.remove('transition-transform');
    });

    hudPill.addEventListener('pointermove', (e) => {
      if (!isDragging) return;
      initialTranslateX = Math.max(-100, Math.min(100, e.clientX - startX));
      initialTranslateY = Math.max(-30, Math.min(30, e.clientY - startY));
      hudPill.style.transform = `translate(${initialTranslateX}px, ${initialTranslateY}px)`;
    });

    const stopDrag = () => {
      if (isDragging) {
        isDragging = false;
        hudPill.classList.add('transition-transform');
      }
    };
    hudPill.addEventListener('pointerup', stopDrag);
    hudPill.addEventListener('pointercancel', stopDrag);
  }

  // Toggle Overlay CTA
  window.triggerOverlayLaunch = function(btn) {
    if (window.Android && window.Android.toggleOverlay) {
      window.Android.toggleOverlay();
    }
  };

  window.updateOverlayButtonState = function(running) {
    isOverlayActive = running;
    const btn = document.getElementById('main-cta-btn');
    if (!btn) return;
    if (running) {
      btn.className = 'w-full py-4 px-6 rounded-full bg-error hover:bg-error-container active:scale-[0.98] transition-all flex items-center justify-between shadow-xl';
      btn.innerHTML = `
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-on-error text-[22px]">stop</span>
          <span class="font-label-mono text-label-mono font-bold text-on-error uppercase tracking-wide">
            DỪNG THANH ĐO NỔI (STOP OVERLAY)
          </span>
        </div>
        <span class="material-symbols-outlined text-on-error text-[20px]">close</span>
      `;
    } else {
      btn.className = 'w-full py-4 px-6 rounded-full bg-primary-container hover:bg-primary-fixed-dim active:scale-[0.98] transition-all flex items-center justify-between shadow-xl';
      btn.innerHTML = `
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-on-primary-container text-[22px]">play_arrow</span>
          <span class="font-label-mono text-label-mono font-bold text-on-primary-container uppercase tracking-wide">
            Khởi động thanh đo nổi ngay (Test Overlay)
          </span>
        </div>
        <span class="material-symbols-outlined text-on-primary-container text-[20px]">arrow_forward</span>
      `;
    }
  };

  // Step Permissions on Tab 0
  window.handleShizukuStep = function() {
    if (window.Android && window.Android.requestShizukuPermission) {
      window.Android.requestShizukuPermission();
    }
  };
  window.handleOverlayStep = function() {
    if (window.Android && window.Android.openOverlaySettings) {
      window.Android.openOverlaySettings();
    }
  };
  window.handleBatteryStep = function() {
    if (window.Android && window.Android.openBatterySettings) {
      window.Android.openBatterySettings();
    }
  };

  // Real-time Telemetry Callback from Android
  window.onTelemetryUpdate = function(jsonStr) {
    try {
      const data = (typeof jsonStr === 'string') ? JSON.parse(jsonStr) : jsonStr;
      if (!data) return;

      // Tab 0 Hero & Telemetry
      if (data.batTemp !== undefined) {
        const heroBat = document.querySelector('.hero-bat-temp');
        if (heroBat) heroBat.textContent = `${data.batTemp.toFixed(1)}°C`;
        const hudTemp = document.querySelector('#hud-item-temp span:last-child');
        if (hudTemp) hudTemp.textContent = `${data.batTemp.toFixed(1)}°C`;
        const cardBatTemp = document.querySelector('.card-bat-temp');
        if (cardBatTemp) cardBatTemp.textContent = `${data.batTemp.toFixed(1)}°C`;
      }
      if (data.batteryPct !== undefined) {
        const topBat = document.getElementById('top-bat-pct');
        if (topBat) topBat.textContent = `${data.batteryPct}%`;
        const hudBat = document.querySelector('#hud-item-battery span:last-child');
        if (hudBat) hudBat.textContent = `${data.batteryPct}%`;
        const cardBat = document.querySelector('.card-bat-pct');
        if (cardBat) cardBat.textContent = `${data.batteryPct}%`;
      }
      if (data.cpuTemp !== undefined) {
        const cpuEl = document.querySelector('.card-cpu-temp');
        if (cpuEl) cpuEl.textContent = `${Math.round(data.cpuTemp)}°C`;
      }
      if (data.fps !== undefined) {
        const hudFps = document.querySelector('#hud-item-fps span:last-child');
        if (hudFps) hudFps.textContent = data.fps.toFixed(1);
      }
      if (data.gpuLoad !== undefined) {
        const hudGpu = document.querySelector('#hud-item-gpu span:last-child');
        if (hudGpu) hudGpu.textContent = `${data.gpuLoad}%`;
      }
      if (data.ramUsedPct !== undefined) {
        const ramBar = document.querySelector('.ram-progress-bar');
        if (ramBar) ramBar.style.width = `${data.ramUsedPct}%`;
        const ramBadge = document.querySelector('.ram-used-badge');
        if (ramBadge) ramBadge.textContent = `${data.ramUsedPct}% ĐÃ DÙNG`;
      }
      if (data.ramAvailGb !== undefined && data.ramTotalGb !== undefined) {
        const ramText = document.querySelector('.ram-avail-text');
        if (ramText) ramText.innerHTML = `RAM Khả dụng: <strong class="text-primary-fixed">${data.ramAvailGb.toFixed(1)} GB</strong> / ${data.ramTotalGb.toFixed(0)} GB`;
        const ramFree = document.querySelector('.ram-free-text');
        if (ramFree) ramFree.textContent = `${data.ramAvailGb.toFixed(1)} GB trống`;
      }
      if (data.isOverlayRunning !== undefined) {
        updateOverlayButtonState(data.isOverlayRunning);
      }
    } catch (e) {
      console.error('onTelemetryUpdate error:', e);
    }
  };

  // Initialization
  function initApp() {
    initSsaaOptions();
    initDragSimulation();

    // Hook up step clicks on Tab 0
    const steps = document.querySelectorAll('#tab-home .rounded-lg');
    steps.forEach((step, idx) => {
      const text = step.textContent || '';
      if (text.includes('Shizuku')) {
        step.style.cursor = 'pointer';
        step.onclick = window.handleShizukuStep;
      } else if (text.includes('Quyền vẽ')) {
        step.style.cursor = 'pointer';
        step.onclick = window.handleOverlayStep;
      } else if (text.includes('tối ưu hóa pin')) {
        step.style.cursor = 'pointer';
        step.onclick = window.handleBatteryStep;
      }
    });

    // Fetch initial data from Android bridge
    if (window.Android && window.Android.getInitialData) {
      try {
        const initData = JSON.parse(window.Android.getInitialData());
        if (initData) {
          if (initData.physW && initData.physH) {
            devicePhysW = initData.physW;
            devicePhysH = initData.physH;
            updateResolutionLabels(devicePhysW, devicePhysH);
          }
          if (initData.soc) {
            const socEl = document.getElementById('top-soc-model');
            if (socEl) socEl.textContent = initData.soc;
          }
          if (initData.refreshRate) {
            const fpsEl = document.getElementById('top-fps-display');
            if (fpsEl) fpsEl.textContent = `${initData.refreshRate} FPS`;
          }
          if (initData.selectedFactor) {
            selectedFactor = initData.selectedFactor;
            updateSsaaCardStyles(selectedFactor);
          }
          if (initData.globalSsaaEnabled !== undefined) {
            const tog = document.getElementById('global-ssaa-toggle');
            if (tog) tog.checked = initData.globalSsaaEnabled;
          }
          if (initData.apps) {
            renderGamesList(initData.apps);
          }
          if (initData.isHudRunning !== undefined) {
            updateOverlayButtonState(initData.isHudRunning);
          }
          if (initData.hudLayoutMode !== undefined) {
            const layoutBtns = document.querySelectorAll('.layout-btn');
            if (layoutBtns[initData.hudLayoutMode]) {
              const modes = ['horizontal', 'compact', 'dot'];
              window.selectLayout(layoutBtns[initData.hudLayoutMode], modes[initData.hudLayoutMode]);
            }
          }
          if (initData.hudColorAccent !== undefined) {
            const dots = document.querySelectorAll('.color-dot');
            if (dots[initData.hudColorAccent]) {
              const colors = ['#d0ef65', '#6dfe9c', '#eec14a', '#ffffff'];
              window.changeThemeColor(colors[initData.hudColorAccent], dots[initData.hudColorAccent]);
            }
          }
        }
      } catch (err) {
        console.error('Failed to parse getInitialData:', err);
      }
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
  } else {
    initApp();
  }
})();
