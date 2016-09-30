//jshint strict: false
module.exports = function(config) {
    config.set({
        frameworks: [ 'jasmine' ],

        basePath: './',

        files: [
//            '../node_modules/angular/angular.js',
//            '../node_modules/angular-mocks/angular-mocks.js',
//            '../node_modules/systemjs/index.js',
            'src/**/*.test.js'
        ],

        autoWatch: true,

        browsers: [ 'PhantomJS' ], // PhantomJS, Chrome, Firefox?

        plugins: [
            'karma-chrome-launcher',
            'karma-firefox-launcher',
            'karma-phantomjs-launcher',
            'karma-jasmine',
            'karma-junit-reporter',
            'karma-commonjs-preprocessor'
        ],

        preprocessors: {
//            'src/**/*.js': ['commonjs']
        },

        junitReporter: {
            outputFile: 'test_out/unit.xml',
            suite: 'unit'
        }

    });
};
