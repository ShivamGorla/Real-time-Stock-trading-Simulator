/* Isolated development-only mock market data for StockSim.
   Strictly read-only: Used when backend market endpoints are offline or in development.
   Never used for order settlement or wallet balance manipulation. */
(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.StockSimMockData = factory();
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var MOCK_NOTICE = 'Illustrative simulated market data — backend market feed unavailable.';

    var DEFAULT_STOCKS = [
        {
            symbol: 'RELIANCE',
            companyName: 'Reliance Industries Ltd',
            currentPrice: 2950.00,
            previousPrice: 2928.00,
            change: 22.00,
            changePercent: 0.75,
            high: 2965.00,
            low: 2915.00,
            volume: 3840210,
            sector: 'Energy / Conglomerate',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'TCS',
            companyName: 'Tata Consultancy Services Ltd',
            currentPrice: 3850.00,
            previousPrice: 3885.00,
            change: -35.00,
            changePercent: -0.90,
            high: 3890.00,
            low: 3830.00,
            volume: 1920400,
            sector: 'Information Technology',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'HDFCBANK',
            companyName: 'HDFC Bank Ltd',
            currentPrice: 1620.00,
            previousPrice: 1612.00,
            change: 8.00,
            changePercent: 0.50,
            high: 1632.00,
            low: 1608.00,
            volume: 5120800,
            sector: 'Banking / Financial Services',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'INFY',
            companyName: 'Infosys Ltd',
            currentPrice: 1540.00,
            previousPrice: 1555.00,
            change: -15.00,
            changePercent: -0.96,
            high: 1560.00,
            low: 1532.00,
            volume: 4210300,
            sector: 'Information Technology',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'ICICIBANK',
            companyName: 'ICICI Bank Ltd',
            currentPrice: 1110.00,
            previousPrice: 1098.00,
            change: 12.00,
            changePercent: 1.09,
            high: 1118.00,
            low: 1095.00,
            volume: 3410100,
            sector: 'Banking / Financial Services',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'TATAMOTORS',
            companyName: 'Tata Motors Ltd',
            currentPrice: 980.00,
            previousPrice: 962.00,
            change: 18.00,
            changePercent: 1.87,
            high: 988.00,
            low: 958.00,
            volume: 6850200,
            sector: 'Automotive',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'ITC',
            companyName: 'ITC Ltd',
            currentPrice: 435.00,
            previousPrice: 436.50,
            change: -1.50,
            changePercent: -0.34,
            high: 438.00,
            low: 433.00,
            volume: 7240900,
            sector: 'FMCG / Diversified',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'SBIN',
            companyName: 'State Bank of India',
            currentPrice: 790.00,
            previousPrice: 782.00,
            change: 8.00,
            changePercent: 1.02,
            high: 795.00,
            low: 780.00,
            volume: 4930100,
            sector: 'Public Sector Banking',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'BHARTIARTL',
            companyName: 'Bharti Airtel Ltd',
            currentPrice: 1320.00,
            previousPrice: 1308.00,
            change: 12.00,
            changePercent: 0.92,
            high: 1328.00,
            low: 1305.00,
            volume: 2780600,
            sector: 'Telecommunications',
            currency: 'INR',
            simulated: true
        },
        {
            symbol: 'WIPRO',
            companyName: 'Wipro Ltd',
            currentPrice: 490.00,
            previousPrice: 496.00,
            change: -6.00,
            changePercent: -1.21,
            high: 498.00,
            low: 488.00,
            volume: 2150800,
            sector: 'Information Technology',
            currency: 'INR',
            simulated: true
        }
    ];

    function clone(obj) {
        return JSON.parse(JSON.stringify(obj));
    }

    return {
        MOCK_NOTICE: MOCK_NOTICE,
        getStocks: function () {
            return clone(DEFAULT_STOCKS);
        },
        getStockBySymbol: function (sym) {
            sym = (sym || '').toUpperCase();
            for (var i = 0; i < DEFAULT_STOCKS.length; i++) {
                if (DEFAULT_STOCKS[i].symbol === sym) return clone(DEFAULT_STOCKS[i]);
            }
            return clone(DEFAULT_STOCKS[0]);
        },
        getOverview: function () {
            var sortedGainers = clone(DEFAULT_STOCKS).sort(function (a, b) {
                return b.changePercent - a.changePercent;
            });
            var topGainer = sortedGainers[0];
            var topLoser = sortedGainers[sortedGainers.length - 1];
            var mostActive = clone(DEFAULT_STOCKS).sort(function (a, b) {
                return b.volume - a.volume;
            })[0];

            return {
                benchmark: {
                    name: 'Simulated NIFTY 50',
                    value: 24185.60,
                    change: 112.40,
                    changePercent: 0.47,
                    simulated: true
                },
                topGainer: topGainer,
                topLoser: topLoser,
                mostActive: mostActive,
                activeCount: DEFAULT_STOCKS.length,
                simulated: true
            };
        }
    };
}));
