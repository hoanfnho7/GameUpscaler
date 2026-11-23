// Antigravity GameUpscaler Web App Bridge & Interactions
(function() {
  'use strict';

  let currentTab = 0;
  let devicePhysW = 1080;
  let devicePhysH = 2400;
  let selectedFactor = 1.15;
  let selectedGamePkg = null;
  let gamesList = [];
  let allInstalledAppsCache = [];
  let activeHudLayout = 'horizontal';
  let isOverlayActive = false;

  // =========================================================================
  // 1. TAB NAVIGATION WITH PERSISTENCE (FIX TAB KICK-BACK BUG)
  // =========================================================================
  window.switchTab = function(idx) {
    currentTab = idx;
    try {
      localStorage.setItem('active_tab_index', idx.toString());
    } catch (_) {}

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

  // =========================================================================
  // 2. THEME & LANGUAGE CONTROLS
  // =========================================================================
  window.toggleTheme = function() {
    const html = document.documentElement;
    const isDark = html.classList.contains('dark');
    const icon = document.getElementById('theme-icon');
    if (isDark) {
      html.classList.remove('dark');
      if (icon) icon.textContent = 'light_mode';
      try { localStorage.setItem('app_theme', 'light'); } catch (_) {}
      if (window.Android && window.Android.setThemeMode) window.Android.setThemeMode(2);
    } else {
      html.classList.add('dark');
      if (icon) icon.textContent = 'dark_mode';
      try { localStorage.setItem('app_theme', 'dark'); } catch (_) {}
      if (window.Android && window.Android.setThemeMode) window.Android.setThemeMode(1);
    }
  };

  let currentLang = 'VI';
  window.toggleLanguage = function() {
    currentLang = (currentLang === 'VI') ? 'EN' : 'VI';
    const langEl = document.getElementById('lang-text');
    if (langEl) langEl.textContent = currentLang;
    try { localStorage.setItem('app_lang', currentLang); } catch (_) {}
    if (window.Android && window.Android.setAppLang) {
      window.Android.setAppLang(currentLang === 'VI' ? 1 : 2);
    }
  };

  // =========================================================================
  // 3. SSAA SCALE OPTIONS
  // =========================================================================
  function initSsaaOptions() {
    const cards = document.querySelectorAll('.ssaa-card');
    cards.forEach(card => {
      card.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        const ratio = parseFloat(this.getAttribute('data-ratio'));
        selectedFactor = ratio;
        if (window.Android && window.Android.setSsaaFactor) {
          window.Android.setSsaaFactor(ratio);
        }
        updateSsaaCardStyles(ratio);
        renderGamesList(gamesList);
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
    const nativeTag = document.getElementById('ssaa-current-native-tag');
    if (nativeTag) {
      nativeTag.textContent = `Gốc: ${physW}×${physH}`;
    }
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

  // =========================================================================
  // 4. REAL GAME WHITELIST RENDERING & APP PICKER MODAL
  // =========================================================================
  function renderGamesList(apps) {
    gamesList = apps || [];
    const container = document.getElementById('game-library-list');
    const badgeCount = document.getElementById('game-count-badge');
    const heroCount = document.getElementById('hero-game-count');

    const countText = `${gamesList.length} Sẵn sàng`;
    if (badgeCount) badgeCount.textContent = countText;
    if (heroCount) heroCount.textContent = `${gamesList.length} Game`;

    if (!container) return;

    if (gamesList.length === 0) {
      container.innerHTML = `
        <div class="p-space-md text-center text-on-surface-variant font-body-sm flex flex-col items-center justify-center gap-2">
          <span class="material-symbols-outlined text-[32px] text-on-surface-variant/60">sports_esports</span>
          <span>Chưa có game nào trong danh sách nhận diện</span>
          <button onclick="openAddGameModal()" class="px-3 py-1.5 rounded-full bg-primary-container text-on-primary-fixed font-label-mono text-xs font-bold mt-1">
            + Thêm game ngay
          </button>
        </div>
      `;
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
          <div class="flex items-center gap-space-sm min-w-0 flex-1 mr-2 cursor-pointer" onclick="handleLaunchGame('${game.pkg}')" title="Chạm để khởi chạy game với SSAA">
            <img src="${game.icon}" class="w-11 h-11 rounded-lg bg-surface-container-highest object-cover shrink-0" alt="${game.name}">
            <div class="flex flex-col min-w-0">
              <span class="font-headline-sm text-headline-sm font-semibold text-on-surface truncate">${game.name}</span>
              <div class="flex items-center gap-2 text-on-surface-variant mt-0.5">
                <span class="font-label-mono text-label-mono text-surface-tint">${scaleLabel}</span>
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

  // Game Scale Modal (Lock per-game resolution)
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
      { val: 1.20, title: 'Khóa 1.20x', sub: 'Hình ảnh rõ nét, khử răng cưa tốt' },
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
    const g = gamesList.find(x => x.pkg === selectedGamePkg);
    if (g) g.customScale = scale;
    renderGamesList(gamesList);
    closeGameScaleModal();
  };

  // Add Game Modal (All Apps Picker)
  window.openAddGameModal = function() {
    const modal = document.getElementById('add-game-modal');
    if (!modal) return;
    modal.classList.remove('hidden');
    const container = document.getElementById('add-game-list-container');
    if (container) container.innerHTML = '<div class="p-4 text-center text-on-surface-variant font-body-sm">Đang tải danh sách ứng dụng trên thiết bị...</div>';
    
    if (window.Android && window.Android.getAllInstalledApps) {
      try {
        const json = window.Android.getAllInstalledApps();
        allInstalledAppsCache = JSON.parse(json) || [];
        renderAddGameList(allInstalledAppsCache);
      } catch (e) {
        console.error('getAllInstalledApps error:', e);
      }
    }
  };

  window.closeAddGameModal = function() {
    const modal = document.getElementById('add-game-modal');
    if (modal) modal.classList.add('hidden');
  };

  function renderAddGameList(list) {
    const container = document.getElementById('add-game-list-container');
    if (!container) return;
    if (!list || list.length === 0) {
      container.innerHTML = '<div class="p-4 text-center text-on-surface-variant font-body-sm">Không tìm thấy ứng dụng phù hợp</div>';
      return;
    }
    let html = '';
    list.forEach(app => {
      const btnText = app.isAdded ? 'Xóa' : '+ Thêm';
      const btnClass = app.isAdded 
        ? 'bg-surface-container-highest text-error hover:bg-surface-bright active:scale-95'
        : 'bg-primary-container text-on-primary-fixed hover:bg-primary-fixed-dim active:scale-95';
      const actionCall = app.isAdded 
        ? `handleRemoveCustomGame('${app.pkg}')`
        : `handleAddCustomGame('${app.pkg}')`;

      html += `
        <div class="flex items-center justify-between p-2.5 rounded-xl bg-surface-container hover:bg-surface-container-high transition-colors">
          <div class="flex items-center gap-3 min-w-0 flex-1 mr-2">
            <img src="${app.icon}" class="w-10 h-10 rounded-lg bg-surface-container-highest object-cover shrink-0" alt="">
            <div class="flex flex-col min-w-0">
              <span class="font-headline-sm text-sm font-semibold text-on-surface truncate">${app.name}</span>
              <span class="font-label-mono text-[11px] text-on-surface-variant truncate">${app.pkg}</span>
            </div>
          </div>
          <button onclick="${actionCall}" class="px-3 py-1.5 rounded-full text-xs font-label-mono font-bold shrink-0 transition-all ${btnClass}">
            ${btnText}
          </button>
        </div>
      `;
    });
    container.innerHTML = html;
  }

  window.filterAddGameList = function(query) {
    const q = (query || '').toLowerCase().trim();
    if (!q) {
      renderAddGameList(allInstalledAppsCache);
      return;
    }
    const filtered = allInstalledAppsCache.filter(a => 
      a.name.toLowerCase().includes(q) || a.pkg.toLowerCase().includes(q)
    );
    renderAddGameList(filtered);
  };

  window.handleAddCustomGame = function(pkg) {
    if (window.Android && window.Android.addCustomGame) {
      window.Android.addCustomGame(pkg);
    }
    const item = allInstalledAppsCache.find(x => x.pkg === pkg);
    if (item) item.isAdded = true;
    renderAddGameList(allInstalledAppsCache);
  };

  window.handleRemoveCustomGame = function(pkg) {
    if (window.Android && window.Android.removeCustomGame) {
      window.Android.removeCustomGame(pkg);
    }
    const item = allInstalledAppsCache.find(x => x.pkg === pkg);
    if (item) item.isAdded = false;
    renderAddGameList(allInstalledAppsCache);
  };

  // =========================================================================
  // 5. HUD CONFIGURATION & LIVE PREVIEW CANVAS
  // =========================================================================
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
      if (targetEl && activeHudLayout !== 'dot') targetEl.classList.remove('hidden');
    } else {
      btn.classList.remove('bg-primary-container');
      btn.classList.add('bg-surface-container-highest');
      dot.classList.remove('bg-on-primary-container', 'translate-x-6');
      dot.classList.add('bg-on-surface-variant', 'translate-x-0');
      if (targetEl) targetEl.classList.add('hidden');
    }

    const metricMap = {
      'hud-item-fps': 'm_fps',
      'hud-item-temp': 'm_bat_temp',
      'hud-item-battery': 'm_watt',
      'hud-item-gpu': 'm_gpu',
      'hud-item-ram': 'm_ram'
    };
    const key = metricMap[targetElementId];
    if (key && window.Android && window.Android.setHudMetric) {
      window.Android.setHudMetric(key, newState);
    }

    // Update active metrics count
    const enabledToggles = document.querySelectorAll('.toggle-btn[data-enabled="true"]').length;
    const totalToggles = document.querySelectorAll('.toggle-btn').length;
    const countEl = document.getElementById('hud-metrics-count');
    if (countEl) countEl.textContent = `${enabledToggles}/${totalToggles} Kích hoạt`;
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
    activeHudLayout = mode;
    document.querySelectorAll('.layout-btn').forEach(b => {
      b.classList.remove('bg-surface-container-high', 'text-primary-fixed', 'shadow-sm');
      b.classList.add('bg-surface-container', 'text-on-surface-variant');
    });
    btn.classList.remove('bg-surface-container', 'text-on-surface-variant');
    btn.classList.add('bg-surface-container-high', 'text-primary-fixed', 'shadow-sm');

    const hudInner = document.getElementById('hud-inner');
    const hudTemp = document.getElementById('hud-item-temp');
    const hudBat = document.getElementById('hud-item-battery');
    const hudGpu = document.getElementById('hud-item-gpu');
    const hudRam = document.getElementById('hud-item-ram');
    const sep1 = document.getElementById('hud-sep-1');
    const sep2 = document.getElementById('hud-sep-2');
    const sep3 = document.getElementById('hud-sep-3');

    let modeIdx = 0;
    if (mode === 'compact') {
      modeIdx = 1;
      if (hudInner) hudInner.className = 'px-3 py-2 rounded-2xl bg-surface-container-lowest/85 backdrop-blur-md shadow-lg grid grid-cols-2 gap-2 text-xs';
      if (sep1) sep1.classList.add('hidden');
      if (sep2) sep2.classList.add('hidden');
      if (sep3) sep3.classList.add('hidden');
      if (hudTemp) hudTemp.classList.remove('hidden');
      if (hudBat) hudBat.classList.remove('hidden');
      if (hudGpu) hudGpu.classList.remove('hidden');
    } else if (mode === 'dot') {
      modeIdx = 2;
      if (hudInner) hudInner.className = 'px-2.5 py-1 rounded-full bg-surface-container-lowest/90 backdrop-blur-md shadow-lg flex items-center gap-1.5';
      if (sep1) sep1.classList.add('hidden');
      if (sep2) sep2.classList.add('hidden');
      if (sep3) sep3.classList.add('hidden');
      if (hudTemp) hudTemp.classList.add('hidden');
      if (hudBat) hudBat.classList.add('hidden');
      if (hudGpu) hudGpu.classList.add('hidden');
      if (hudRam) hudRam.classList.add('hidden');
    } else {
      modeIdx = 0;
      if (hudInner) hudInner.className = 'px-3 py-1.5 rounded-full bg-surface-container-lowest/85 backdrop-blur-md shadow-lg flex items-center gap-2.5 max-w-full overflow-x-auto no-scrollbar';
      if (sep1) sep1.classList.remove('hidden');
      if (sep2) sep2.classList.remove('hidden');
      if (sep3) sep3.classList.remove('hidden');
      if (hudTemp) hudTemp.classList.remove('hidden');
      if (hudBat) hudBat.classList.remove('hidden');
      if (hudGpu) hudGpu.classList.remove('hidden');
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
    const fpsDot = document.querySelector('#hud-item-fps span:first-child');
    if (fpsDot) fpsDot.style.backgroundColor = colorHex;

    const colorDots = Array.from(document.querySelectorAll('.color-dot'));
    const idx = colorDots.indexOf(btn);
    if (idx >= 0 && window.Android && window.Android.setHudColorAccent) {
      window.Android.setHudColorAccent(idx);
    }
  };

  // Draggable HUD Simulation in Tab 2 Preview
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
      initialTranslateX = Math.max(-120, Math.min(120, e.clientX - startX));
      initialTranslateY = Math.max(-40, Math.min(40, e.clientY - startY));
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

  // Toggle Overlay CTA Button
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

  // =========================================================================
  // 6. REAL-TIME TELEMETRY CALLBACK FROM ANDROID
  // =========================================================================
  window.onTelemetryUpdate = function(jsonStr) {
    try {
      const data = (typeof jsonStr === 'string') ? JSON.parse(jsonStr) : jsonStr;
      if (!data) return;

      // Real SoC & Chip
      if (data.soc) {
        const topSoc = document.getElementById('top-soc-model');
        if (topSoc) topSoc.textContent = data.soc;
        const homeChip = document.getElementById('home-device-chip');
        if (homeChip) homeChip.textContent = data.soc;
        const tab2Soc = document.getElementById('tab2-soc-summary');
        if (tab2Soc && data.batTemp) {
          tab2Soc.textContent = `${data.soc} • ${data.batTemp.toFixed(1)}°C`;
        }
      }

      // Live Display Refresh Rate
      if (data.refreshRate) {
        const topFps = document.getElementById('top-fps-display');
        if (topFps) topFps.textContent = `${data.refreshRate} Hz`;
        const dispRes = document.getElementById('card-display-res');
        if (dispRes) dispRes.textContent = `${devicePhysW} x ${devicePhysH} • ${data.refreshRate}Hz`;
      }

      // Real GPU Name
      if (data.gpuName) {
        const cardGpu = document.getElementById('card-gpu-name');
        if (cardGpu) cardGpu.textContent = data.gpuName;
      }

      // Real Battery & Temp
      if (data.batteryPct !== undefined) {
        const cardBat = document.getElementById('card-bat-pct');
        if (cardBat) cardBat.textContent = `${data.batteryPct}%`;
        const hudBat = document.querySelector('#hud-item-battery span:last-child');
        if (hudBat) hudBat.textContent = `${data.batteryPct}%`;
      }
      if (data.batTemp !== undefined) {
        const heroBat = document.getElementById('hero-bat-temp');
        if (heroBat) heroBat.textContent = `${data.batTemp.toFixed(1)}°C`;
        const cardBatTemp = document.getElementById('card-bat-temp');
        if (cardBatTemp) cardBatTemp.textContent = `${data.batTemp.toFixed(1)}°C`;
        const hudTemp = document.querySelector('#hud-item-temp span:last-child');
        if (hudTemp) hudTemp.textContent = `${data.batTemp.toFixed(1)}°C`;
      }
      if (data.batStatus) {
        const batBadge = document.getElementById('bat-status-badge');
        if (batBadge) batBadge.textContent = data.batStatus;
        const heroLabel = document.getElementById('hero-bat-label');
        if (heroLabel) heroLabel.textContent = `Pin (${data.batStatus})`;
      }
      if (data.batMode) {
        const batMode = document.getElementById('card-bat-mode');
        if (batMode) batMode.textContent = data.batMode;
      }

      // Real CPU Temp
      if (data.cpuTemp !== undefined) {
        const cpuEl = document.getElementById('card-cpu-temp');
        if (cpuEl) cpuEl.textContent = `${Math.round(data.cpuTemp)}°C`;
      }

      // Real RAM Usage
      if (data.ramUsedPct !== undefined) {
        const ramBar = document.getElementById('ram-progress-bar');
        if (ramBar) ramBar.style.width = `${data.ramUsedPct}%`;
        const ramBadge = document.getElementById('ram-used-badge');
        if (ramBadge) ramBadge.textContent = `${data.ramUsedPct}% ĐÃ DÙNG`;
      }
      if (data.ramAvailGb !== undefined && data.ramTotalGb !== undefined) {
        const ramAvail = document.getElementById('ram-avail-text');
        if (ramAvail) ramAvail.innerHTML = `RAM Khả dụng: <strong class="text-primary-fixed">${data.ramAvailGb.toFixed(1)} GB</strong> / ${data.ramTotalGb.toFixed(0)} GB`;
        const ramFree = document.getElementById('ram-free-text');
        if (ramFree) ramFree.textContent = `${data.ramAvailGb.toFixed(1)} GB trống`;
      }

      // Shizuku & Overlay Permissions State
      if (data.isShizukuReady !== undefined) {
        const heroShizuku = document.getElementById('hero-shizuku-status');
        if (heroShizuku) heroShizuku.textContent = data.isShizukuReady ? '100%' : 'Chưa cấp';
        const s1 = document.getElementById('step1-badge');
        if (s1) {
          s1.textContent = data.isShizukuReady ? 'Đã kích hoạt' : 'Chưa cấp';
          s1.className = data.isShizukuReady 
            ? 'px-2 py-1 rounded-full bg-primary-container/20 text-primary-fixed font-label-badge text-label-badge font-bold'
            : 'px-2 py-1 rounded-full bg-error-container/20 text-error font-label-badge text-label-badge font-bold';
        }
        const eye = document.getElementById('home-shizuku-eyebrow');
        if (eye) eye.textContent = `TRẠNG THÁI DỊCH VỤ · SSAA ENGINE (${data.isShizukuReady ? 'SẴN SÀNG' : 'CHƯA KÍCH HOẠT'})`;
        const tit = document.getElementById('home-shizuku-title');
        if (tit) tit.textContent = data.isShizukuReady ? 'Khử răng cưa đang sẵn sàng · Đã kết nối Shizuku' : 'Cần kích hoạt Shizuku để áp dụng SSAA';
      }
      if (data.isOverlayGranted !== undefined) {
        const s2 = document.getElementById('step2-badge');
        if (s2) {
          s2.textContent = data.isOverlayGranted ? 'Đã cấp' : 'Chưa cấp';
          s2.className = data.isOverlayGranted 
            ? 'px-2 py-1 rounded-full bg-primary-container/20 text-primary-fixed font-label-badge text-label-badge font-bold'
            : 'px-2 py-1 rounded-full bg-error-container/20 text-error font-label-badge text-label-badge font-bold';
        }
      }

      // Stepper Summary
      const readyCount = (data.isShizukuReady ? 1 : 0) + (data.isOverlayGranted ? 1 : 0) + 1;
      const stepSummary = document.getElementById('stepper-summary');
      if (stepSummary) stepSummary.textContent = `${readyCount}/3 Sẵn sàng`;

      // Live HUD Overlay Running Status
      if (data.isOverlayRunning !== undefined) {
        updateOverlayButtonState(data.isOverlayRunning);
      }

      // Apps list update
      if (data.apps) {
        renderGamesList(data.apps);
      }
    } catch (e) {
      console.error('onTelemetryUpdate error:', e);
    }
  };

  // =========================================================================
  // 7. INITIALIZATION
  // =========================================================================
  function initApp() {
    initSsaaOptions();
    initDragSimulation();

    // Restore saved active tab (Fix navigation kick-back)
    try {
      const savedTab = localStorage.getItem('active_tab_index');
      if (savedTab !== null) {
        switchTab(parseInt(savedTab, 10));
      } else {
        switchTab(0);
      }
    } catch (_) {
      switchTab(0);
    }

    // Restore saved theme
    try {
      const savedTheme = localStorage.getItem('app_theme');
      if (savedTheme === 'light') {
        document.documentElement.classList.remove('dark');
        const icon = document.getElementById('theme-icon');
        if (icon) icon.textContent = 'light_mode';
      }
    } catch (_) {}

    // Restore saved lang
    try {
      const savedLang = localStorage.getItem('app_lang');
      if (savedLang) {
        currentLang = savedLang;
        const langEl = document.getElementById('lang-text');
        if (langEl) langEl.textContent = currentLang;
      }
    } catch (_) {}

    // Fetch initial data from Android bridge
    if (window.Android && window.Android.getInitialData) {
      try {
        const initData = JSON.parse(window.Android.getInitialData());
        if (initData) {
          if (initData.physW && initData.physH) {
            devicePhysW = initData.physW;
            devicePhysH = initData.physH;
            updateResolutionLabels(devicePhysW, devicePhysH);
            const previewRes = document.getElementById('preview-game-res');
            if (previewRes) previewRes.textContent = `Màn hình mô phỏng • ${devicePhysH}x${devicePhysW}`;
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
            const modes = ['horizontal', 'compact', 'dot'];
            const targetModeIdx = initData.hudLayoutMode;
            if (layoutBtns[targetModeIdx]) {
              window.selectLayout(layoutBtns[targetModeIdx], modes[targetModeIdx]);
            }
          }
          if (initData.hudColorAccent !== undefined) {
            const dots = document.querySelectorAll('.color-dot');
            if (dots[initData.hudColorAccent]) {
              const colors = ['#d0ef65', '#6dfe9c', '#eec14a', '#ffffff'];
              window.changeThemeColor(colors[initData.hudColorAccent], dots[initData.hudColorAccent]);
            }
          }
          // Process full initial telemetry
          window.onTelemetryUpdate(initData);
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
