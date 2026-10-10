document.addEventListener('DOMContentLoaded', function () {

    // --- Mobile nav toggle ---
    var toggle = document.querySelector('.nav-toggle');
    var navLinks = document.querySelector('.nav-links');
    if (toggle) {
        toggle.addEventListener('click', function () {
            navLinks.classList.toggle('open');
        });
    }

    // --- Navbar scroll effect ---
    var navbar = document.querySelector('.navbar');
    window.addEventListener('scroll', function () {
        navbar.classList.toggle('scrolled', window.scrollY > 40);
    }, { passive: true });

    // --- Health check ---
    var statusEl = document.getElementById('server-status');
    fetch('/api/health')
        .then(function (res) { return res.json(); })
        .then(function (data) {
            if (data.status === 'UP') {
                statusEl.textContent = '● Online';
                statusEl.style.color = '#22d3ee';
            }
        })
        .catch(function () {
            statusEl.textContent = '○ Offline';
            statusEl.style.color = '#ef4444';
        });

    // --- Scroll reveal ---
    var revealTargets = document.querySelectorAll(
        '.feature-card, .placeholder-card, .cta-card, .section-header'
    );

    revealTargets.forEach(function (el) {
        el.classList.add('reveal');
    });

    var observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

    revealTargets.forEach(function (el) {
        observer.observe(el);
    });

    // --- Close mobile nav on link click ---
    document.querySelectorAll('.nav-links a').forEach(function (link) {
        link.addEventListener('click', function () {
            navLinks.classList.remove('open');
        });
    });
});
