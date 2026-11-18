/* Read-only AI Trading Assistant chat widget on dashboard.html.
   Endpoints: POST /api/assistant/chat (CSRF protected).
   Security: Escapes all model text via textContent only — never injects raw LLM output into innerHTML. */
document.addEventListener('DOMContentLoaded', function () {
    var panel = document.getElementById('assistant-panel');
    if (!panel) return;

    var messagesEl = document.getElementById('assistant-messages');
    var formEl = document.getElementById('assistant-form');
    var inputEl = document.getElementById('assistant-input');
    var sendBtn = document.getElementById('assistant-send');
    var newBtn = document.getElementById('assistant-new-btn');
    var loadingEl = document.getElementById('assistant-loading');
    var errorEl = document.getElementById('assistant-error');
    var starterBtns = panel.querySelectorAll('[data-assistant-prompt]');

    var conversationId = null;
    var busy = false;

    function csrf() {
        var m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : '';
    }

    function showError(msg) {
        errorEl.textContent = msg;
        errorEl.hidden = false;
    }

    function clearError() {
        errorEl.textContent = '';
        errorEl.hidden = true;
    }

    function setBusy(state) {
        busy = state;
        inputEl.disabled = state;
        sendBtn.disabled = state;
        newBtn.disabled = state;
        loadingEl.hidden = !state;
        starterBtns.forEach(function (b) { b.disabled = state; });
    }

    function appendBubble(role, text, toolsUsed) {
        var wrap = document.createElement('div');
        wrap.className = 'chat-bubble chat-bubble-' + (role === 'user' ? 'user' : 'assistant');

        var label = document.createElement('span');
        label.className = 'chat-role';
        label.textContent = role === 'user' ? 'You' : 'AI Assistant';
        wrap.appendChild(label);

        var body = document.createElement('p');
        body.className = 'chat-text';
        // Strictly textContent — never innerHTML — to prevent XSS from model-generated content
        body.textContent = text;
        wrap.appendChild(body);

        if (role !== 'user' && Array.isArray(toolsUsed) && toolsUsed.length > 0) {
            var meta = document.createElement('span');
            meta.className = 'chat-meta';
            meta.textContent = 'Checked: ' + toolsUsed.join(', ');
            wrap.appendChild(meta);
        }

        messagesEl.appendChild(wrap);
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function resetConversation(focusInput) {
        conversationId = null;
        clearError();
        while (messagesEl.firstChild) {
            messagesEl.removeChild(messagesEl.firstChild);
        }
        appendBubble('assistant', 'Ask me about your portfolio, wallet balance, recent orders, stock prices, or preview a simulated trade.');
        if (focusInput) inputEl.focus();
    }

    function sendMessage(rawText) {
        var text = (rawText || '').trim();
        if (!text || busy) return;
        if (text.length > 4000) {
            showError('Message must be 4000 characters or fewer.');
            return;
        }

        clearError();
        appendBubble('user', text);
        inputEl.value = '';
        setBusy(true);

        var payload = { message: text };
        if (conversationId) {
            payload.conversationId = conversationId;
        }

        var controller = new AbortController();
        var timeout = setTimeout(function () { controller.abort(); }, 60000);
        fetch('/api/assistant/chat', {
            signal: controller.signal,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrf()
            },
            body: JSON.stringify(payload)
        }).then(function (res) {
            return res.text().then(function (t) {
                var body = null;
                try { body = t ? JSON.parse(t) : null; } catch (e) { /* non-JSON */ }
                return { ok: res.ok, status: res.status, body: body };
            });
        }).then(function (r) {
            if (r.status === 401) {
                showError('Your session expired. Please sign in again.');
                return;
            }
            if (r.status === 503 || (r.body && r.body.error === 'ASSISTANT_DISABLED')) {
                showError((r.body && r.body.message) || 'The AI Trading Assistant is currently disabled.');
                return;
            }
            if (!r.ok || !r.body || !r.body.success) {
                var errMsg = (r.body && (r.body.message || r.body.error)) || 'The AI assistant could not answer right now. Please try again.';
                showError(errMsg);
                return;
            }
            if (r.body.conversationId) {
                conversationId = r.body.conversationId;
            }
            appendBubble('assistant', r.body.message || '', r.body.toolsUsed);
        }).catch(function () {
            showError('Could not reach the StockSim server. Please try again.');
        }).finally(function () {
            clearTimeout(timeout);
            setBusy(false);
            inputEl.focus();
        });
    }

    formEl.addEventListener('submit', function (e) {
        e.preventDefault();
        sendMessage(inputEl.value);
    });

    newBtn.addEventListener('click', function () {
        if (!busy) resetConversation(true);
    });

    starterBtns.forEach(function (btn) {
        btn.addEventListener('click', function () {
            sendMessage(btn.getAttribute('data-assistant-prompt'));
        });
    });

    resetConversation();
});
