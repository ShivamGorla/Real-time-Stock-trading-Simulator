document.addEventListener('DOMContentLoaded', function () {

    function getCsrfToken() {
        const match = document.cookie.match(new RegExp('(^| )XSRF-TOKEN=([^;]+)'));
        return match ? match[2] : '';
    }

    const registerForm = document.getElementById('register-form');
    if (registerForm) {
        registerForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const errorDiv = document.getElementById('form-error');
            errorDiv.style.display = 'none';

            const data = {
                name: document.getElementById('name').value,
                username: document.getElementById('username').value,
                email: document.getElementById('email').value,
                password: document.getElementById('password').value,
                confirmPassword: document.getElementById('confirmPassword').value
            };

            fetch('/api/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': getCsrfToken()
                },
                body: JSON.stringify(data)
            }).then(res => {
                if (res.ok) {
                    window.location.href = '/login.html?registered=true';
                } else {
                    res.json().then(err => {
                        errorDiv.textContent = err.error || 'Registration failed. Check your inputs.';
                        errorDiv.style.display = 'block';
                    });
                }
            });
        });
    }

    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        const params = new URLSearchParams(window.location.search);
        if (params.get('registered') === 'true') {
            const msgDiv = document.getElementById('form-success');
            msgDiv.textContent = 'Registration successful! Please sign in.';
            msgDiv.style.display = 'block';
        }

        loginForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const errorDiv = document.getElementById('form-error');
            errorDiv.style.display = 'none';

            const formData = new URLSearchParams();
            formData.append('username', document.getElementById('username').value);
            formData.append('password', document.getElementById('password').value);

            fetch('/api/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-XSRF-TOKEN': getCsrfToken()
                },
                body: formData.toString()
            }).then(res => {
                if (res.ok) {
                    window.location.href = '/dashboard.html';
                } else {
                    errorDiv.textContent = 'Invalid username or password.';
                    errorDiv.style.display = 'block';
                }
            });
        });
    }

    const dashboardProfile = document.getElementById('dashboard-profile');
    if (dashboardProfile) {
        fetch('/api/user/me', {
            headers: { 'X-XSRF-TOKEN': getCsrfToken() }
        }).then(res => {
            if (res.ok) {
                return res.json();
            } else {
                window.location.href = '/login.html';
                throw new Error('Not authenticated');
            }
        }).then(user => {
            document.getElementById('user-name').textContent = user.name;
            document.getElementById('user-balance').textContent = '₹' + user.virtualBalance.toLocaleString();
        });

        document.getElementById('logout-btn').addEventListener('click', function(e) {
            e.preventDefault();
            fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': getCsrfToken() }
            }).then(() => window.location.href = '/');
        });
    }
});
