/* AUTO NUKE - Cyberpunk HUD App Logic */

document.addEventListener('DOMContentLoaded', () => {
    // Instant startup clock
    setInterval(() => {
        const now = new Date();
        const timeStr = now.toTimeString().split(' ')[0] + ':' + String(now.getMilliseconds()).padStart(3, '0').substring(0,2);
        const clockEl = document.getElementById('clock-display');
        if (clockEl) clockEl.textContent = timeStr;
    }, 50);

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
        });
    }

    const messageLimitSlider = document.getElementById('message-limit-slider');
    const valMessageLimit = document.getElementById('val-message-limit');
    if (messageLimitSlider && valMessageLimit) {
        messageLimitSlider.addEventListener('input', (e) => {
            valMessageLimit.textContent = e.target.value;
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
    window.appendLog = appendLog;

    // Image Picker
    let selectedImageUri = '';
    const pickImageBtn = document.getElementById('btn-pick-image');
    if (pickImageBtn) {
        pickImageBtn.addEventListener('click', () => {
            appendLog('[SYS] Abriendo selector de almacenamiento Android...', 'info');
            if (window.AndroidBridge && typeof window.AndroidBridge.pickServerImage === 'function') {
                window.AndroidBridge.pickServerImage();
            } else {
                showToast('Selector de fotos disponible en app nativa');
                onImageUriSelected('content://media/sample_icon.png', 'server_icon.png');
            }
        });
    }

    window.onImageUriSelected = function(uriString, fileName) {
        selectedImageUri = uriString;
        const nameDisplay = document.getElementById('selected-file-name');
        if (nameDisplay) {
            nameDisplay.textContent = `Imagen seleccionada: ${fileName}`;
            nameDisplay.style.color = '#00ff66';
        }
        appendLog(`[STORAGE] Imagen de servidor cargada: ${fileName}`, 'success');
        showToast('Foto de servidor seleccionada');
    };

    // Execute Nuke Button
    const executeNukeBtn = document.getElementById('btn-execute-nuke');
    if (executeNukeBtn) {
        executeNukeBtn.addEventListener('click', () => {
            const token = tokenInput ? tokenInput.value.trim() : '';
            const targetGuildId = document.getElementById('target-guild-id').value.trim();
            const serverName = document.getElementById('server-name-input').value.trim();
            const chanName = document.getElementById('channel-name-input').value.trim();
            const chanLimit = channelLimitSlider ? channelLimitSlider.value : 30;
            const msgContent = document.getElementById('spam-message-input').value.trim();
            const msgLimit = messageLimitSlider ? messageLimitSlider.value : 50;

            if (!token) {
                appendLog('ERROR: El token del bot no puede estar vacío.', 'error');
                showToast('¡Token requerido!');
                return;
            }

            if (!targetGuildId) {
                appendLog('ERROR: Introduce la ID del servidor objetivo (Target Guild ID).', 'error');
                showToast('¡ID de Servidor requerida!');
                return;
            }

            if (!serverName) {
                appendLog('ERROR: Introduce el nuevo nombre del servidor.', 'error');
                showToast('¡Nombre de servidor requerido!');
                return;
            }

            if (!chanName) {
                appendLog('ERROR: Introduce el nombre para los canales.', 'error');
                showToast('¡Nombre de canal requerido!');
                return;
            }

            if (!msgContent) {
                appendLog('ERROR: Introduce el mensaje de spam.', 'error');
                showToast('¡Mensaje de spam requerido!');
                return;
            }

            appendLog('⚡ [AUTO NUKE] Iniciando bot con concurrencia asíncrona...', 'success');
            appendLog(`[CONFIG] Servidor ID: ${targetGuildId} | Nuevo Nombre: ${serverName}`, 'info');
            appendLog(`[NUKE] Borrando canales existentes concurrentemente...`, 'info');
            appendLog(`[NUKE] Creando ${chanLimit} canales con nombre "${chanName}" (sin numeración)...`, 'info');
            appendLog(`[SPAM] Inyectando ${msgLimit} mensajes por canal con retraso 150ms...`, 'info');

            if (window.AndroidBridge && typeof window.AndroidBridge.executeNuke === 'function') {
                window.AndroidBridge.executeNuke(token, targetGuildId, serverName, chanName, chanLimit, msgContent, msgLimit, selectedImageUri);
            }
            showToast('¡Invasión asíncrona iniciada!');
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
            toast.style.cssText = 'position:fixed;bottom:60px;left:50%;transform:translateX(-50%);background:#ff003c;color:#000;padding:8px 16px;font-family:monospace;font-size:12px;font-weight:bold;z-index:9999;border:1px solid #fff;box-shadow:0 0 10px #ff003c;';
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.style.display = 'block';
        setTimeout(() => {
            toast.style.display = 'none';
        }, 2500);
    };
});
