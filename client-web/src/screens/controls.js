import { setControlPreset } from '../input/keyboard.js';

export const RANGE_COLORS = {
  shot: {
    name: 'Shot',
    color: '#f87171',
    bg: 'rgba(180, 0, 0, 0.08)',
    border: '#b40000',
    tagHtml: '<span class="range-tag tag-shot"><span class="range-tag-dot" style="background: #b40000;"></span>Red Ground Line</span>',
    rangeDesc: 'Red line reflects ground shot range.'
  },
  lob: {
    name: 'Lob',
    color: '#93c5fd',
    bg: 'rgba(0, 80, 255, 0.08)',
    border: '#0050ff',
    tagHtml: '<span class="range-tag tag-lob"><span class="range-tag-split"></span>Blue Air / Yellow Ends</span>',
    rangeDesc: 'Blue/yellow trajectory reflects lob range (blue region in air passes over defenders between yellow block/catch ends).'
  },
  ability1: {
    name: 'Ability 1',
    color: '#86efac',
    bg: 'rgba(0, 255, 0, 0.06)',
    border: '#00ff00',
    tagHtml: '<span class="range-tag tag-a1"><span class="range-tag-dot" style="background: #00ff00;"></span>Green Range Circle</span>',
    rangeDesc: 'Green circle reflects ability cast range.'
  },
  ability2: {
    name: 'Ability 2',
    color: '#d8b4fe',
    bg: 'rgba(128, 0, 128, 0.10)',
    border: '#800080',
    tagHtml: '<span class="range-tag tag-a2"><span class="range-tag-dot" style="background: #800080; border: 1px solid #c084fc;"></span>Purple Range Circle</span>',
    rangeDesc: 'Purple circle reflects ability cast range.'
  },
  steal: {
    name: 'Steal',
    color: '#99f6e4',
    bg: 'rgba(64, 224, 208, 0.08)',
    border: '#40e0d0',
    tagHtml: '<span class="range-tag tag-steal"><span class="range-tag-dot" style="background: #40e0d0;"></span>Turquoise Steal Radius</span>',
    rangeDesc: 'Turquoise circle reflects steal radius.'
  }
};

export const CONTROLS_DATA = {
  'rts': {
    name: 'RTS Style',
    subtitle: 'Point & Click Movement + Ability Hotkeys',
    summary: 'Classic Real-Time Strategy controls. Click on the court to direct your Titan, and target your shots and abilities with your mouse cursor.',
    sections: [
      {
        title: 'Movement & Positioning',
        icon: '👟',
        items: [
          { action: 'Move to Location', keys: ['Right Click (RMB)'], desc: 'Commands your Titan to path and run to the clicked court position.' },
          { action: 'Move to Cursor (Field Titans)', keys: ['X'], desc: 'Paths Titan towards mouse cursor when playing field classes. (Note: when playing Goalie, X buys your next upgrade).' },
          { action: 'Move to Ball', keys: ['C'], desc: 'Automatically paths your Titan directly towards the loose ball.' }
        ]
      },
      {
        title: 'Ball Handling & Shooting',
        icon: '🏀',
        items: [
          {
            action: 'Direct Shot / Pass',
            keys: ['Left Click (LMB)', 'O'],
            rangeType: 'shot',
            desc: 'Fires a fast, direct ground shot or pass towards your mouse cursor.'
          },
          {
            action: 'Lob Shot / Pass',
            keys: ['E', 'P'],
            rangeType: 'lob',
            desc: 'Lofts a high-arc lob pass towards the cursor. The ball flies over defenders in the airborne blue zone between landing/blocking yellow ends.'
          },
          {
            action: 'Call for Ball (Without Ball)',
            keys: ['E'],
            desc: 'When you do not possess the ball, press E to call for an AI teammate in possession to pass to you. (Active only in hybrid human/AI games).'
          }
        ]
      },
      {
        title: 'Abilities & Combat',
        icon: '⚡',
        items: [
          {
            action: 'Primary Ability (A1)',
            keys: ['Q', '1'],
            rangeType: 'ability1',
            desc: 'Casts your Titan\'s primary active skill towards the cursor within your green range indicator.'
          },
          {
            action: 'Secondary Ability (A2)',
            keys: ['W', '2'],
            rangeType: 'ability2',
            desc: 'Casts your Titan\'s secondary active skill towards the cursor within your purple range indicator.'
          },
          {
            action: 'Steal Ball',
            keys: ['T'],
            rangeType: 'steal',
            desc: 'Attempts to steal the ball from an opposing ball carrier within your turquoise steal radius.'
          }
        ]
      },
      {
        title: 'Sprinting & Stamina',
        icon: '💨',
        items: [
          { action: 'Sprint / Boost (Hold)', keys: ['F', '3'], desc: 'Hold down to sprint at increased movement speed, consuming stamina.' },
          { action: 'Toggle Boost Lock', keys: ['R', '4', 'G'], desc: 'Toggles sprinting on or off continuously without needing to hold a key.' }
        ]
      },
      {
        title: 'Camera & Targeting',
        icon: '🎥',
        items: [
          { action: 'Toggle Camera Lock', keys: ['Spacebar'], desc: 'Toggles between locking the camera on your Titan and free court scrolling.' },
          { action: 'Switch Titan / Target', keys: ['Z'], desc: 'Cycles between controlled Titans or target focus.' },
          { action: 'View Instructions', keys: ['I'], desc: 'Displays in-game HUD instructions and controls.' }
        ]
      },
      {
        title: 'Goalie RTS Lane Commander',
        icon: '🏰',
        items: [
          { action: 'Buy Next Build Upgrade', keys: ['X'], desc: 'Instantly purchases the next queued upgrade node from your Build Order when playing as Goalie.' },
          { action: 'Open Upgrade Trees', keys: ['Tab'], desc: 'Opens Goalie tech trees. Hold Tab to view the opposing Goalie\'s purchased tree.' }
        ]
      }
    ]
  },
  'keyboard': {
    name: 'Keyboard Style',
    subtitle: 'Pure WASD Movement + Mouse Aim / Shoot',
    summary: 'Pure WASD movement controls. Move your Titan directly with W, A, S, and D (or arrow keys) while aiming and firing with your mouse cursor.',
    sections: [
      {
        title: 'Movement & Positioning',
        icon: '⌨️',
        items: [
          { action: 'Directional Movement', keys: ['W', 'A', 'S', 'D'], desc: 'Move your Titan Up, Left, Down, and Right across the court (Arrow Keys also supported).' },
          { action: 'Move to Cursor (Field Titans)', keys: ['X'], desc: 'Paths Titan toward cursor when playing field classes. (Note: when playing Goalie, X buys your next upgrade).' },
          { action: 'Move to Ball', keys: ['C'], desc: 'Automatically moves your Titan toward the loose ball.' }
        ]
      },
      {
        title: 'Ball Handling & Shooting',
        icon: '🏀',
        items: [
          {
            action: 'Direct Shot / Pass',
            keys: ['Left Click (LMB)', 'O'],
            rangeType: 'shot',
            desc: 'Fires a fast, direct ground shot or pass towards your mouse cursor.'
          },
          {
            action: 'Lob Shot / Pass',
            keys: ['Right Click (RMB)', 'P'],
            rangeType: 'lob',
            desc: 'Lofts a high-arc lob pass towards the cursor. Flies safely over opponents in the blue airborne zone between yellow ends.'
          },
          {
            action: 'Call for Ball (Without Ball)',
            keys: ['E'],
            desc: 'When you do not possess the ball, press E to call for an AI teammate in possession to pass to you. (Active only in hybrid human/AI games).'
          }
        ]
      },
      {
        title: 'Abilities & Combat',
        icon: '⚡',
        items: [
          {
            action: 'Primary Ability (A1)',
            keys: ['E', '1'],
            rangeType: 'ability1',
            desc: 'Casts your Titan\'s primary active skill towards the cursor within your green range indicator.'
          },
          {
            action: 'Secondary Ability (A2)',
            keys: ['R', '2'],
            rangeType: 'ability2',
            desc: 'Casts your Titan\'s secondary active skill towards the cursor within your purple range indicator.'
          },
          {
            action: 'Steal Ball',
            keys: ['Q'],
            rangeType: 'steal',
            desc: 'Attempts to steal the ball from an opposing ball carrier within your turquoise steal radius.'
          }
        ]
      },
      {
        title: 'Sprinting & Stamina',
        icon: '💨',
        items: [
          { action: 'Sprint / Boost (Hold)', keys: ['F', '3'], desc: 'Hold down to sprint at increased movement speed, consuming stamina.' },
          { action: 'Toggle Boost Lock', keys: ['G', '4'], desc: 'Toggles sprinting on or off continuously without needing to hold a key.' }
        ]
      },
      {
        title: 'Camera & Targeting',
        icon: '🎥',
        items: [
          { action: 'Toggle Camera Lock', keys: ['Spacebar'], desc: 'Toggles between locking the camera on your Titan and free court scrolling.' },
          { action: 'Switch Titan / Target', keys: ['Z'], desc: 'Cycles between controlled Titans or target focus.' },
          { action: 'View Instructions', keys: ['I'], desc: 'Displays in-game HUD instructions and controls.' }
        ]
      },
      {
        title: 'Goalie RTS Lane Commander',
        icon: '🏰',
        items: [
          { action: 'Buy Next Build Upgrade', keys: ['X'], desc: 'Instantly purchases the next queued upgrade node from your Build Order when playing as Goalie.' },
          { action: 'Open Upgrade Trees', keys: ['Tab'], desc: 'Opens Goalie tech trees. Hold Tab to view the opposing Goalie\'s purchased tree.' }
        ]
      }
    ]
  },
  'mobile-single': {
    name: 'Mobile (Single Joystick)',
    subtitle: 'Virtual Movement Stick + Tap/Swipe Court Gestures',
    summary: 'Streamlined mobile touchscreen controls. Guide your Titan with the left joystick and shoot by tapping or dragging directly on the court.',
    sections: [
      {
        title: 'Movement & Sprinting',
        icon: '🕹️',
        items: [
          { action: 'Virtual Joystick', keys: ['Left Screen'], desc: 'Drag the virtual analog stick with your left thumb to move your Titan.' },
          { action: 'Sprint Boost Switch', keys: ['Boost Switch'], desc: 'Toggle the sprint switch on the right side to sprint and boost speed.' }
        ]
      },
      {
        title: 'Court Tap Gestures (Shooting)',
        icon: '👆',
        items: [
          {
            action: 'Tap to Shoot / Pass',
            keys: ['Tap Screen'],
            rangeType: 'shot',
            desc: 'Quick-tap anywhere on the court to fire a direct ground shot or pass towards that location.'
          },
          {
            action: 'Drag to Lob Pass',
            keys: ['Swipe Court'],
            rangeType: 'lob',
            desc: 'Touch and swipe away on the court to execute a high-arc lob pass over obstacles (blue airborne trajectory).'
          },
          {
            action: 'Call for Ball (Without Ball)',
            keys: ['CALL Button'],
            desc: 'In hybrid human/AI games when without the ball, a green CALL button appears where shot/lob normally goes. Tap it to call for an AI teammate in possession to pass you the ball.'
          }
        ]
      },
      {
        title: 'Action Buttons',
        icon: '⚡',
        items: [
          {
            action: 'Primary Ability (A1)',
            keys: ['A1 Button'],
            rangeType: 'ability1',
            desc: 'Triggers primary ability towards the nearest opponent or movement vector (green range indicator).'
          },
          {
            action: 'Secondary Ability (A2)',
            keys: ['A2 Button'],
            rangeType: 'ability2',
            desc: 'Triggers secondary ability (purple range indicator).'
          },
          {
            action: 'Steal Ball',
            keys: ['Steal Button'],
            rangeType: 'steal',
            desc: 'Steals the ball from an opponent when inside your turquoise radius.'
          }
        ]
      },
      {
        title: 'Goalie Actions',
        icon: '🏰',
        items: [
          { action: 'Buy Next Build Upgrade', keys: ['Upgrade Button'], desc: 'Tap the green Upgrade button to immediately purchase your next queued build order node.' },
          { action: 'Open Tech Trees', keys: ['Tree Tabs'], desc: 'Tap the upgrade tree tabs along the bottom of the screen to open and buy upgrades.' }
        ]
      }
    ]
  },
  'mobile-double': {
    name: 'Mobile (Double Joystick)',
    subtitle: 'Dual Joysticks (Move & Aim) + Shot Button',
    summary: 'Dual joystick mobile controls. Steer your Titan with the left joystick, aim targeting with the right joystick, and tap the shot button.',
    sections: [
      {
        title: 'Dual Joysticks (Move & Aim)',
        icon: '🕹️',
        items: [
          { action: 'Move Joystick', keys: ['Left Screen'], desc: 'Drag the left virtual stick to steer your Titan across the court.' },
          { action: 'Aim Joystick', keys: ['Right Screen'], desc: 'Drag the right virtual stick to aim your shot line and ability targeting reticle.' }
        ]
      },
      {
        title: 'Shooting & Sprinting',
        icon: '🏀',
        items: [
          {
            action: 'Fire Shot / Pass',
            keys: ['Shot Button'],
            rangeType: 'shot',
            desc: 'Tap the dedicated orange shot button to fire directly along your aim reticle (red ground range line).'
          },
          {
            action: 'Call for Ball (Without Ball)',
            keys: ['CALL Button'],
            desc: 'In hybrid human/AI games when without the ball, a green CALL button replaces the Shot button. Tap it to call for an AI teammate in possession to pass you the ball.'
          },
          { action: 'Sprint Boost Switch', keys: ['Boost Switch'], desc: 'Toggle the sprint switch on the right side to sprint and boost speed.' }
        ]
      },
      {
        title: 'Action Buttons',
        icon: '⚡',
        items: [
          {
            action: 'Primary Ability (A1)',
            keys: ['A1 Button'],
            rangeType: 'ability1',
            desc: 'Casts your primary ability directly in your currently aimed direction (green range indicator).'
          },
          {
            action: 'Secondary Ability (A2)',
            keys: ['A2 Button'],
            rangeType: 'ability2',
            desc: 'Casts your secondary ability in your currently aimed direction (purple range indicator).'
          },
          {
            action: 'Steal Ball',
            keys: ['Steal Button'],
            rangeType: 'steal',
            desc: 'Steals the ball from an opponent when inside your turquoise radius.'
          }
        ]
      },
      {
        title: 'Goalie Actions',
        icon: '🏰',
        items: [
          { action: 'Buy Next Build Upgrade', keys: ['Upgrade Button'], desc: 'Tap the green Upgrade button to immediately purchase your next queued build order node.' },
          { action: 'Open Tech Trees', keys: ['Tree Tabs'], desc: 'Tap the upgrade tree tabs along the bottom of the screen to open and buy upgrades.' }
        ]
      }
    ]
  }
};

let currentViewingPreset = 'rts';

export function getChosenControlPreset() {
  const select = document.getElementById('controls-select');
  if (select && select.value) {
    return select.value;
  }
  return sessionStorage.getItem('controlPreset') || 'rts';
}

export function openControlsModal(preset) {
  const modal = document.getElementById('controls-modal');
  const modeOverlay = document.getElementById('mode-overlay');
  if (!modal) return;

  currentViewingPreset = preset || getChosenControlPreset();
  renderControlsModal();

  modal.style.display = 'flex';
  if (modeOverlay) {
    modeOverlay.style.pointerEvents = 'none';
  }
}

export function closeControlsModal() {
  const modal = document.getElementById('controls-modal');
  const modeOverlay = document.getElementById('mode-overlay');
  if (modal) {
    modal.style.display = 'none';
  }
  if (modeOverlay) {
    modeOverlay.style.pointerEvents = 'auto';
  }
}

function renderControlsModal() {
  const data = CONTROLS_DATA[currentViewingPreset] || CONTROLS_DATA['rts'];
  const chosenPreset = getChosenControlPreset();
  const isCurrentlyChosen = currentViewingPreset === chosenPreset;

  // Update tabs active state
  const tabButtons = document.querySelectorAll('#controls-preset-tabs .stats-tab-btn');
  tabButtons.forEach(btn => {
    const preset = btn.getAttribute('data-preset');
    btn.classList.toggle('active', preset === currentViewingPreset);
  });

  // Update banner badge & apply button
  const bannerText = document.getElementById('controls-preset-banner-text');
  const applyBtn = document.getElementById('controls-apply-btn');
  if (bannerText) {
    if (isCurrentlyChosen) {
      bannerText.innerHTML = `✓ Active Layout: <span style="color: #2ed573; font-weight: 700;">${data.name}</span>`;
      bannerText.parentElement.style.borderColor = 'rgba(46, 213, 115, 0.4)';
      bannerText.parentElement.style.background = 'rgba(46, 213, 115, 0.12)';
    } else {
      bannerText.innerHTML = `Viewing: <span style="color: #ff9f1c; font-weight: 700;">${data.name}</span>`;
      bannerText.parentElement.style.borderColor = 'rgba(255, 159, 28, 0.4)';
      bannerText.parentElement.style.background = 'rgba(255, 159, 28, 0.12)';
    }
  }

  if (applyBtn) {
    if (isCurrentlyChosen) {
      applyBtn.style.display = 'none';
    } else {
      applyBtn.style.display = 'inline-block';
      applyBtn.textContent = `Select ${data.name}`;
    }
  }

  // Render sections
  const bodyEl = document.getElementById('controls-modal-body');
  if (!bodyEl) return;

  let html = `
    <div style="margin-bottom: 12px; padding: 10px 14px; background: rgba(0, 0, 0, 0.3); border-radius: 10px; border-left: 3px solid #ff9f1c;">
      <div style="font-size: 13px; font-weight: 600; color: #ff9f1c; margin-bottom: 2px;">${data.subtitle}</div>
      <div style="font-size: 12px; color: #94a3b8; line-height: 1.4;">${data.summary}</div>
    </div>
    <div class="controls-guide-container">
  `;

  data.sections.forEach(sec => {
    html += `
      <div class="controls-section">
        <div class="controls-section-title">
          <span>${sec.icon}</span>
          <span>${sec.title}</span>
        </div>
    `;

    sec.items.forEach(item => {
      const keysHtml = item.keys.map(k => `<span class="key-kbd">${k}</span>`).join(' <span style="color:#64748b; font-size:10px;">or</span> ');
      const rc = item.rangeType ? RANGE_COLORS[item.rangeType] : null;

      const rowStyle = rc
        ? `style="border-left: 3px solid ${rc.border}; background: ${rc.bg};"`
        : '';
      const rowClass = rc ? 'controls-row color-coded' : 'controls-row';
      const tagHtml = rc ? rc.tagHtml : '';
      const rangeNotice = rc
        ? `<div style="font-size: 11px; color: ${rc.color}; margin-top: 3px; font-style: italic;">• ${rc.rangeDesc}</div>`
        : '';

      html += `
        <div class="${rowClass}" ${rowStyle}>
          <div class="controls-desc">
            <div style="font-weight: 600; color: #ffffff; margin-bottom: 2px; display: flex; align-items: center; flex-wrap: wrap;">
              <span>${item.action}</span>
              ${tagHtml}
            </div>
            <div style="font-size: 12px; color: #cbd5e1;">${item.desc}</div>
            ${rangeNotice}
          </div>
          <div class="controls-key">${keysHtml}</div>
        </div>
      `;
    });

    html += `</div>`;
  });

  html += `</div>`;
  bodyEl.innerHTML = html;
}

export function initControlsModal() {
  const helpBtn = document.getElementById('controls-help-btn');
  if (helpBtn) {
    helpBtn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      openControlsModal(getChosenControlPreset());
    });
  }

  const closeBtn = document.getElementById('controls-modal-close-btn');
  if (closeBtn) {
    closeBtn.addEventListener('click', () => closeControlsModal());
  }

  const doneBtn = document.getElementById('controls-modal-done-btn');
  if (doneBtn) {
    doneBtn.addEventListener('click', () => closeControlsModal());
  }

  // Preset switch tabs inside modal
  const tabButtons = document.querySelectorAll('#controls-preset-tabs .stats-tab-btn');
  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const preset = btn.getAttribute('data-preset');
      if (preset && CONTROLS_DATA[preset]) {
        currentViewingPreset = preset;
        renderControlsModal();
      }
    });
  });

  // Apply button inside modal
  const applyBtn = document.getElementById('controls-apply-btn');
  if (applyBtn) {
    applyBtn.addEventListener('click', async () => {
      const select = document.getElementById('controls-select');
      if (select) {
        select.value = currentViewingPreset;
      }
      await setControlPreset(currentViewingPreset);
      renderControlsModal();
    });
  }

  // Close on Escape key
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      const modal = document.getElementById('controls-modal');
      if (modal && modal.style.display !== 'none') {
        closeControlsModal();
      }
    }
  });

  // When controls-select changes on homescreen, keep current viewing preset synchronized
  const select = document.getElementById('controls-select');
  if (select) {
    select.addEventListener('change', () => {
      const modal = document.getElementById('controls-modal');
      if (modal && modal.style.display !== 'none') {
        currentViewingPreset = select.value;
        renderControlsModal();
      }
    });
  }
}
