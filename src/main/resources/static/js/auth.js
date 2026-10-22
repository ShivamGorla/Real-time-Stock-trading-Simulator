/* Auth pages and dashboard. Endpoints unchanged:
   POST /api/auth/register (JSON), POST /api/auth/login (form), POST /api/auth/logout, GET /api/user/me */
document.addEventListener('DOMContentLoaded', function () {

    var inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });

    function getCsrfToken() {
        var match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    function showAlert(el, text) { el.textContent = text; el.hidden = false; }
    function hideAlert(el) { if (el) { el.hidden = true; el.textContent = ''; } }

    function setBusy(btn, busy, busyText) {
        if (!btn.dataset.label) btn.dataset.label = btn.textContent;
        btn.disabled = busy;
        btn.setAttribute('aria-busy', String(busy));
        btn.textContent = busy ? busyText : btn.dataset.label;
    }

    function setFieldError(input, hasError) {
        var field = input.closest('.field');
        if (!field) return;
        field.classList.toggle('has-error', hasError);
        input.setAttribute('aria-invalid', String(hasError));
        var msg = field.querySelector('.field-error');
        if (msg) {
            if (!msg.id) msg.id = input.id + '-error';
            input.setAttribute('aria-describedby', [input.dataset.hint, hasError ? msg.id : ''].filter(Boolean).join(' '));
        }
    }

    // Remember any hint ids so error toggling keeps them
    document.querySelectorAll('.input[aria-describedby]').forEach(function (i) { i.dataset.hint = i.getAttribute('aria-describedby'); });

    // Clear a field's error as soon as the person edits it
    document.querySelectorAll('.input').forEach(function (input) {
        input.addEventListener('input', function () { setFieldError(input, false); });
    });

    // Show / hide password
    document.querySelectorAll('[data-reveal]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var input = document.getElementById(btn.dataset.reveal);
            var show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.textContent = show ? 'Hide' : 'Show';
            btn.setAttribute('aria-pressed', String(show));
            btn.setAttribute('aria-label', (show ? 'Hide ' : 'Show ') + 'password');
        });
    });

    /* ---------- Register ---------- */
    var registerForm = document.getElementById('register-form');
    if (registerForm) {
        registerForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var errorDiv = document.getElementById('form-error');
            hideAlert(errorDiv);

            var f = {
                name: document.getElementById('name'),
                username: document.getElementById('username'),
                email: document.getElementById('email'),
                password: document.getElementById('password'),
                confirmPassword: document.getElementById('confirmPassword')
            };
            var firstBad = null;
            Object.keys(f).forEach(function (k) {
                var input = f[k];
                var bad = !input.checkValidity() || (k !== 'password' && k !== 'confirmPassword' && !input.value.trim());
                if (k === 'confirmPassword') bad = !input.value || input.value !== f.password.value;
                setFieldError(input, bad);
                if (bad && !firstBad) firstBad = input;
            });
            if (firstBad) { firstBad.focus(); return; }

            var btn = registerForm.querySelector('[type="submit"]');
            setBusy(btn, true, 'Creating account…');

            fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': getCsrfToken() },
                body: JSON.stringify({
                    name: f.name.value.trim(),
                    username: f.username.value.trim(),
                    email: f.email.value.trim(),
                    password: f.password.value,
                    confirmPassword: f.confirmPassword.value
                })
            }).then(function (res) {
                if (res.ok) {
                    window.location.href = '/login.html?registered=true';
                    return;
                }
                return res.json().catch(function () { return {}; }).then(function (err) {
                    var msg = err.error || err.message;
                    if (!msg && err.errors && typeof err.errors === 'object') {
                        msg = Object.values(err.errors).join(' ');
                    }
                    showAlert(errorDiv, msg || 'We couldn’t create your account. Check the details above and try again.');
                    setBusy(btn, false);
                });
            }).catch(function () {
                showAlert(errorDiv, 'Can’t reach the server. Check that StockSim is running and try again.');
                setBusy(btn, false);
            });
        });
    }

    /* ---------- Login ---------- */
    var loginForm = document.getElementById('login-form');
    if (loginForm) {
        var params = new URLSearchParams(window.location.search);
        if (params.get('registered') === 'true') {
            showAlert(document.getElementById('form-success'), 'Account created. Sign in to open your dashboard.');
        }

        loginForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var errorDiv = document.getElementById('form-error');
            hideAlert(errorDiv);

            var user = document.getElementById('username');
            var pass = document.getElementById('password');
            setFieldError(user, !user.value.trim());
            setFieldError(pass, !pass.value);
            if (!user.value.trim()) { user.focus(); return; }
            if (!pass.value) { pass.focus(); return; }

            var btn = loginForm.querySelector('[type="submit"]');
            setBusy(btn, true, 'Signing in…');

            var formData = new URLSearchParams();
            formData.append('username', user.value.trim());
            formData.append('password', pass.value);

            fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': getCsrfToken() },
                body: formData.toString()
            }).then(function (res) {
                if (res.ok) {
                    window.location.href = '/dashboard.html';
                } else {
                    showAlert(errorDiv, 'That username and password don’t match. Check both and try again.');
                    pass.value = '';
                    pass.focus();
                    setBusy(btn, false);
                }
            }).catch(function () {
                showAlert(errorDiv, 'Can’t reach the server. Check that StockSim is running and try again.');
                setBusy(btn, false);
            });
        });
    }

    /* ---------- Dashboard ---------- */
    var dashboardProfile = document.getElementById('dashboard-profile');
    if (dashboardProfile) {
        var hour = new Date().getHours();
        document.getElementById('greeting').textContent =
            hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

        function formatDate(v) {
            if (!v) return 'Not available';
            var d = Array.isArray(v) ? new Date(v[0], v[1] - 1, v[2]) : new Date(v);
            return isNaN(d) ? 'Not available' : d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
        }
        function initials(name) {
            return (name || '?').trim().split(/\s+/).slice(0, 2).map(function (p) { return p.charAt(0).toUpperCase(); }).join('');
        }

        fetch('/api/user/me', { headers: { 'X-XSRF-TOKEN': getCsrfToken() } })
            .then(function (res) {
                if (!res.ok) {
                    window.location.href = '/login.html';
                    throw new Error('Not authenticated');
                }
                return res.json();
            })
            .then(function (user) {
                document.getElementById('user-name').textContent = user.name;
                document.getElementById('user-chip-name').textContent = user.username;
                document.getElementById('user-initials').textContent = initials(user.name);
                document.getElementById('user-balance').textContent = inr.format(Number(user.virtualBalance) || 0);
                document.getElementById('user-username').textContent = user.username;
                document.getElementById('user-email').textContent = user.email;
                document.getElementById('user-since').textContent = formatDate(user.createdAt);
            })
            .catch(function () { /* redirected above, or network error leaves skeletons */ });

        function logout(e) {
            e.preventDefault();
            fetch('/api/auth/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': getCsrfToken() } })
                .finally(function () { window.location.href = '/'; });
        }
        document.getElementById('logout-btn').addEventListener('click', logout);
        var smBtn = document.getElementById('logout-btn-sm');
        if (smBtn) { smBtn.hidden = false; smBtn.addEventListener('click', logout); }
    }
});
