/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


var commonConfig = require('./webpack.common.js');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var webpackMerge = require('webpack-merge');

module.exports = webpackMerge(commonConfig, {
    devtool: 'cheap-module-source-map',
    entry: {
        'peoplesearch.ng': './src/modules/peoplesearch/main.dev',
        'changepassword.ng': './src/modules/changepassword/changepassword.module',
        'configeditor.ng': './src/modules/configeditor/configeditor.module',
        'helpdesk.ng': './src/modules/helpdesk/main.dev'
    },
    plugins: [
        // Don't forget to add this to karma.conf.js
        new CopyWebpackPlugin([
            { from: 'node_modules/@uirouter/angularjs/release/angular-ui-router.js', to: 'vendor/' },
            { from: 'node_modules/angular/angular.js', to: 'vendor/' },
            { from: 'node_modules/angular-aria/angular-aria.js', to: 'vendor/' },
            { from: 'node_modules/angular-translate/dist/angular-translate.js', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ux-ias/dist/ux-ias.css', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ng-ias/dist/ng-ias.js', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ias-icons/dist/ias-icons.css', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ias-icons/dist/fonts', to: 'vendor/fonts' }
        ])
    ]
});
