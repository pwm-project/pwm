/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var webpack = require('webpack');
var webpackConfig = require('../webpack.config.js')({}, {});
var path = require("path");
var os = require('os');

module.exports = function (config) {
    config.set({
        // base path that will be used to resolve all patterns (eg. files, exclude)
        basePath: '..',

        // frameworks to use
        // available frameworks: https://npmjs.org/browse/keyword/karma-adapter
        frameworks: [ 'jasmine' ],

        // list of files / patterns to load in the browser
        files: [
            'test/karma-test-suite.ts'
        ],

        exclude: [],

        // preprocess matching files before serving them to the browser
        // available preprocessors: https://npmjs.org/browse/keyword/karma-preprocessor
        preprocessors: {
            "**/*.ts": ["webpack", "sourcemap"]
        },

        // fix typescript serving video/mp2t mime type
        mime: {
            'text/x-typescript': ['ts', 'tsx']
        },

        webpack: {
            mode: 'development',
            devtool: 'inline-source-map',
            resolve: webpackConfig.resolve,
            module: webpackConfig.module,
            optimization: {
                minimize: false
            }
        },

        webpackMiddleware: {
            // display no info to console (only warnings and errors)
            noInfo: true,
            stats: {
                colors: true
            }
        },

        // test results reporter to use
        // possible values: 'dots', 'progress'
        // available reporters: https://npmjs.org/browse/keyword/karma-reporter
        reporters: ['kjhtml', 'spec'],

        // web server port
        port: 9876,

        // enable / disable colors in the output (reporters and logs)
        colors: true,

        // level of logging
        // possible values: config.LOG_DISABLE || config.LOG_ERROR || config.LOG_WARN || config.LOG_INFO || config.LOG_DEBUG
        logLevel: config.LOG_INFO,

        // enable / disable watching file and executing tests whenever any file changes
        autoWatch: true,

        // start these browsers
        // available browser launchers: https://npmjs.org/browse/keyword/karma-launcher
        browsers: ['Chrome_with_debug_plugins'],

        // Provides the ability to install plugins in Chrome (such as JetBrains debugger), and have them stick around
        // between launches:
        customLaunchers: {
            Chrome_with_debug_plugins: {
                base: 'Chrome',
                chromeDataDir: path.resolve(os.homedir(), '.karma/customLaunchers/Chrome_with_debug_plugins')
            }
        },

        // Continuous Integration mode
        // if true, Karma captures browsers, runs the tests and exits
        singleRun: false,

        // Concurrency level
        // how many browser should be started simultaneous
        concurrency: Infinity
    })
};
