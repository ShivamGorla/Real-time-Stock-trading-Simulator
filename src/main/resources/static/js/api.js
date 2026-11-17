/* Centralized REST API Client for StockSim.
   Handles credentials, CSRF token attachment, HTTP error mapping, and safe fallback. */
(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.StockSimAPI = factory();
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    function getCsrfToken() {
        var match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    async function request(endpoint, options) {
        options = options || {};
        options.credentials = 'same-origin';
        options.headers = options.headers || {};

        var method = (options.method || 'GET').toUpperCase();
        if (method !== 'GET' && method !== 'HEAD') {
            var csrf = getCsrfToken();
            if (csrf && !options.headers['X-XSRF-TOKEN']) {
                options.headers['X-XSRF-TOKEN'] = csrf;
            }
        }

        try {
            var response = await fetch(endpoint, options);

            if (response.status === 401) {
                if (window.location.pathname.indexOf('login.html') === -1 &&
                    window.location.pathname.indexOf('register.html') === -1) {
                    window.location.href = '/login.html?expired=true';
                }
                var err401 = new Error('Session expired or unauthenticated');
                err401.status = 401;
                throw err401;
            }

            if (!response.ok) {
                var errorBody = null;
                try {
                    errorBody = await response.json();
                } catch (e) {
                    /* Not JSON */
                }
                var error = new Error((errorBody && (errorBody.message || errorBody.error)) || ('HTTP error ' + response.status));
                error.status = response.status;
                error.data = errorBody;
                throw error;
            }

            var contentType = response.headers.get('content-type');
            if (contentType && contentType.indexOf('application/json') !== -1) {
                return await response.json();
            }
            return await response.text();
        } catch (err) {
            throw err;
        }
    }

    return {
        getCsrfToken: getCsrfToken,
        request: request,

        // User profile and authoritative virtual balance
        getUserProfile: function () {
            return request('/api/user/me');
        },

        // Stock catalog probe
        getStocks: async function () {
            try {
                var stocks = await request('/api/stocks');
                return {
                    source: 'live',
                    stocks: stocks,
                    isMock: false
                };
            } catch (err) {
                // If backend trading endpoints are unmerged or offline, fallback safely to isolated mock data
                if (window.StockSimMockData) {
                    return {
                        source: 'mock',
                        stocks: window.StockSimMockData.getStocks(),
                        isMock: true,
                        notice: window.StockSimMockData.MOCK_NOTICE
                    };
                }
                throw err;
            }
        },

        // Single stock probe
        getStockBySymbol: async function (symbol) {
            try {
                var stock = await request('/api/stocks/' + encodeURIComponent(symbol));
                return { source: 'live', stock: stock, isMock: false };
            } catch (err) {
                if (window.StockSimMockData) {
                    return {
                        source: 'mock',
                        stock: window.StockSimMockData.getStockBySymbol(symbol),
                        isMock: true,
                        notice: window.StockSimMockData.MOCK_NOTICE
                    };
                }
                throw err;
            }
        },

        // Market overview probe
        getMarketOverview: async function () {
            try {
                var overview = await request('/api/stocks/overview');
                return { source: 'live', data: overview, isMock: false };
            } catch (err) {
                if (window.StockSimMockData) {
                    return {
                        source: 'mock',
                        data: window.StockSimMockData.getOverview(),
                        isMock: true,
                        notice: window.StockSimMockData.MOCK_NOTICE
                    };
                }
                throw err;
            }
        }
    };
}));
