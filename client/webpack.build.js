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
var UglifyJsPlugin = require('uglifyjs-webpack-plugin');
var webpack = require('webpack');
var webpackMerge = require('webpack-merge');

module.exports = webpackMerge(commonConfig, {
    devtool: 'source-map',
    entry: {
        'peoplesearch.ng': './src/modules/peoplesearch/main',
        'changepassword.ng': './src/modules/changepassword/changepassword.module',
        'configeditor.ng': './src/modules/configeditor/configeditor.module',
        'helpdesk.ng': './src/modules/helpdesk/main'
    },
    plugins: [
        new CopyWebpackPlugin([
            { from: 'node_modules/@microfocus/ux-ias/dist/ux-ias.css', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ng-ias/dist/ng-ias.js', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ias-icons/dist/ias-icons.css', to: 'vendor/' },
            { from: 'node_modules/@microfocus/ias-icons/dist/fonts', to: 'vendor/fonts' }
        ]),
        new UglifyJsPlugin({
            sourceMap: true,
            uglifyOptions: {
                compress: {warnings: false},
                comments: false
            }
        })
    ]
});
