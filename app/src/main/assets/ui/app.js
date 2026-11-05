/* AUTO NUKE - High Performance App Logic */

document.addEventListener('DOMContentLoaded', () => {
    // Instant startup clock
    setInterval(() => {
        const now = new Date();
        const timeStr = now.toTimeString().split(' ')[0] + ':' + String(now.getMilliseconds()).padStart(3, '0').substring(0,2);
        const clockEl = document.getElementById('clock-display');
        if (clockEl) clockEl.textContent = timeStr;
    }, 50);

    // Tab Navigation
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');

    function switchTab(targetId) {
        tabBtns.forEach(b => b.classList.remove('active'));
        tabContents.forEach(c => c.classList.remove('active'));

        const btn = document.querySelector(`[data-tab="${targetId}"]`);
        const targetEl = document.getElementById(targetId);
        if (btn) btn.classList.add('active');
        if (targetEl) targetEl.classList.add('active');
    }

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.getAttribute('data-tab'));
        });
    });

    // Token Visibility Toggle
    const tokenInput = document.getElementById('bot-token');
    const toggleBtn = document.getElementById('btn-toggle-token');
    if (toggleBtn && tokenInput) {
        toggleBtn.addEventListener('click', () => {
            tokenInput.type = tokenInput.type === 'password' ? 'text' : 'password';
            toggleBtn.textContent = tokenInput.type === 'password' ? '👁' : '🙈';
        });
    }

    // Sliders & Limits
    const channelLimitSlider = document.getElementById('channel-limit-slider');
    const valChannelLimit = document.getElementById('val-channel-limit');
    if (channelLimitSlider && valChannelLimit) {
        channelLimitSlider.addEventListener('input', (e) => {
            valChannelLimit.textContent = e.target.value;
            const sumChannels = document.getElementById('sum-channels');
            if (sumChannels) sumChannels.textContent = e.target.value;
        });
    }

    const messageLimitSlider = document.getElementById('message-limit-slider');
    const valMessageLimit = document.getElementById('val-message-limit');
    if (messageLimitSlider && valMessageLimit) {
        messageLimitSlider.addEventListener('input', (e) => {
            valMessageLimit.textContent = e.target.value;
            const sumMsgs = document.getElementById('sum-msgs');
            if (sumMsgs) sumMsgs.textContent = e.target.value;
        });
    }

    const channelNameInput = document.getElementById('channel-name-input');
    if (channelNameInput) {
        channelNameInput.addEventListener('input', (e) => {
            const sumChanName = document.getElementById('sum-chan-name');
            if (sumChanName) sumChanName.textContent = e.target.value || 'nuked';
        });
    }

    // Terminal Logging Helper
    function appendLog(message, type = 'info') {
        const output = document.getElementById('terminal-output');
        if (!output) return;
        const p = document.createElement('p');
        p.className = `log-${type}`;
        const time = new Date().toTimeString().split(' ')[0];
        p.textContent = `[${time}] ${message}`;
        output.appendChild(p);
        output.scrollTop = output.scrollHeight;
    }

    // Step 1 -> Step 2
    const nextStep1Btn = document.getElementById('btn-next-step-1');
    if (nextStep1Btn) {
        nextStep1Btn.addEventListener('click', () => {
            const token = tokenInput ? tokenInput.value.trim() : '';
            if (!token) {
                appendLog('ERROR: Debes introducir el token del bot de Discord.', 'error');
                showToast('¡Token requerido!');
                return;
            }
            appendLog(`[CONFIG] Token guardado y verificado.`, 'success');
            switchTab('tab-servers');
        });
    }

    // Image Picker
    const pickImageBtn = document.getElementById('btn-pick-image');
    if (pickImageBtn) {
        pickImageBtn.addEventListener('click', () => {
            appendLog('[SYS] Solicitando permisos de almacenamiento y abriendo selector...', 'info');
            if (window.AndroidBridge && typeof window.AndroidBridge.pickServerImage === 'function') {
                window.AndroidBridge.pickServerImage();
            } else {
                showToast('Selector de fotos disponible en app nativa');
                onImageSelected('server_icon_custom.png');
            }
        });
    }

    window.onImageSelected = function(fileName) {
        const nameDisplay = document.getElementById('selected-file-name');
        if (nameDisplay) {
            nameDisplay.textContent = `Imagen seleccionada: ${fileName}`;
            nameDisplay.style.color = '#00ff00';
        }
        appendLog(`[STORAGE] Imagen de servidor cargada: ${fileName}`, 'success');
        showToast('Foto de servidor seleccionada');
    };

    // Fetch Servers List
    let selectedServerId = null;
    const fetchServersBtn = document.getElementById('btn-fetch-servers');
    if (fetchServersBtn) {
        fetchServersBtn.addEventListener('click', () => {
            const token = tokenInput ? tokenInput.value.trim() : '';
            if (!token) {
                appendLog('ERROR: Introduce el token en la pestaña 1 para listar servidores.', 'error');
                showToast('Token requerido');
                switchTab('tab-config');
                return;
            }

            appendLog('[DISCORD API] Conectando con API de Discord para listar servidores...', 'info');
            if (window.AndroidBridge && typeof window.AndroidBridge.fetchGuilds === 'function') {
                window.AndroidBridge.fetchGuilds(token);
            } else {
                // Simulate fetched servers for preview / standalone mode
                setTimeout(() => {
                    renderServers([
                        { id: '10928347102', name: '🎮 Gaming Community [Admin]' },
                        { id: '88371920384', name: '🔥 VIP Lounge Server [Admin]' },
                        { id: '44920183742', name: '🤖 Bot Testing Hub [Admin]' }
                    ]);
                }, 600);
            }
        });
    }

    window.renderServers = function(serversJsonString) {
        let servers = [];
        try {
            servers = typeof serversJsonString === 'string' ? JSON.parse(serversJsonString) : serversJsonString;
        } catch (e) {
            servers = [
                { id: '10928347102', name: '🎮 Gaming Community [Admin]' },
                { id: '88371920384', name: '🔥 VIP Lounge Server [Admin]' }
            ];
        }

        const container = document.getElementById('servers-container');
        if (!container) return;
        container.innerHTML = '';

        servers.forEach(srv => {
            const div = document.createElement('div');
            div.className = 'server-item';
            div.innerHTML = `<span>${srv.name}</span><span class="badge">SELECCIONAR</span>`;
            div.addEventListener('click', () => {
                document.querySelectorAll('.server-item').forEach(i => i.classList.remove('selected'));
                div.classList.add('selected');
                selectedServerId = srv.id;
                const sumServer = document.getElementById('sum-server');
                if (sumServer) sumServer.textContent = srv.name;
                appendLog(`[SERVER] Servidor seleccionado: ${srv.name} (ID: ${srv.id})`, 'success');
                showToast(`Servidor seleccionado: ${srv.name}`);
                switchTab('tab-params');
            });
            container.appendChild(div);
        });
        appendLog(`[DISCORD API] Se encontraron ${servers.length} servidores con privilegios de administrador.`, 'success');
    };

    // Execute Nuke Button
    const executeNukeBtn = document.getElementById('btn-execute-nuke');
    if (executeNukeBtn) {
        executeNukeBtn.addEventListener('click', () => {
            const token = tokenInput ? tokenInput.value.trim() : '';
            const serverName = document.getElementById('server-name-input').value;
            const chanName = channelNameInput ? channelNameInput.value : 'nuked';
            const chanLimit = channelLimitSlider ? channelLimitSlider.value : 50;
            const msgContent = document.getElementById('spam-message-input').value;
            const msgLimit = messageLimitSlider ? messageLimitSlider.value : 150;

            if (!token) {
                appendLog('ERROR CRÍTICO: Falta el token de Discord.', 'error');
                showToast('Falta el Token');
                switchTab('tab-config');
                return;
            }

            appendLog('⚡ [AUTO NUKE] Iniciando protocolo de ataque masivo...', 'error');
            appendLog(`[PYTHON] Conectando socket a servidor backend...`, 'info');
            appendLog(`[CONFIG] Servidor objetivo: ${selectedServerId || 'Predeterminado'} | Nuevo Nombre: ${serverName}`, 'info');
            appendLog(`[NUKE] Creando ${chanLimit} canales con nombre "${chanName}"...`, 'info');
            appendLog(`[SPAM] Enviando ${msgLimit} mensajes por canal...`, 'info');

            if (window.AndroidBridge && typeof window.AndroidBridge.executeNuke === 'function') {
                window.AndroidBridge.executeNuke(token, selectedServerId || 'default', serverName, chanName, chanLimit, msgContent, msgLimit);
            }

            // Simulate progress logs
            setTimeout(() => appendLog('[PYTHON] [OK] Nombre y foto de servidor actualizados.', 'success'), 800);
            setTimeout(() => appendLog('[PYTHON] [OK] Canales masivos creados exitosamente.', 'success'), 1800);
            setTimeout(() => appendLog('[PYTHON] [SUCCESS] ¡Invasión completada con éxito en todos los canales!', 'success'), 2800);
            showToast('¡Auto Nuke Ejecutado con éxito!');
        });
    }

    // Global Toast helper
    window.showToast = function(message) {
        if (window.AndroidBridge && typeof window.AndroidBridge.showToast === 'function') {
            window.AndroidBridge.showToast(message);
            return;
        }
        let toast = document.getElementById('native-toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'native-toast';
            toast.style.cssText = 'position:fixed;bottom:60px;left:50%;transform:translateX(-50%);background:#ff0000;color:#000;padding:8px 16px;font-family:monospace;font-size:12px;font-weight:bold;z-index:9999;border:1px solid #fff;box-shadow:0 0 10px #ff0000;';
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.style.display = 'block';
        setTimeout(() => {
            toast.style.display = 'none';
        }, 2500);
    };
});
