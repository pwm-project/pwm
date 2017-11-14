/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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


var HtmlWebpackPlugin = require('html-webpack-plugin');
var autoPrefixer = require('autoprefixer');
var path = require('path');
var webpack = require('webpack');

var outDir = path.resolve(__dirname, 'dist');

module.exports = {
    devServer: {
        contentBase: outDir,
        outputPath: outDir,
        port: 4000,
        historyApiFallback: true
    },
    // Externals copied to /dist via CopyWebpackPlugin
    externals:
    {
        'angular': true,
        // Wrapped in window because of hyphens
        'angular-ui-router': 'window["angular-ui-router"]',
        'angular-translate': 'window["angular-translate"]'
    },
    module: {
        preLoaders: [
            {
                test: /\.ts$/,
                loader: 'tslint'
            }
        ],
        loaders: [
            {
                test: /\.ts$/,
                loader: 'ts',
                exclude: /node_modules/
            },
            {
                test: /index\.html$/,
                loader: 'html',
                exclude: /node_modules/
            },
            {
                test: /\.html$/,
                loader: 'ngtemplate?relativeTo=' + (path.resolve(__dirname, './src')) + '/!html',
                exclude: /index\.html$/
            },
            {
                test: /\.scss$/,
                loaders: [ 'style', 'css', 'sass', 'postcss' ]
            },
            {
                test: /\.json/,
                loaders: [ 'json' ]
            },
            {
                test: /\.(png|jpg|jpeg|gif|svg)$/,
                loaders: [ 'url?limit=25000' ]
            }
        ]
    },
    // [name] is replaced by entry point name
    output: {
        filename: '[name].js',
        path: outDir
    },
    plugins: [
        new HtmlWebpackPlugin({
            chunks: ['peoplesearch.ng'],
            filename: 'peoplesearch.html',
            template: 'index.html',
            // title: 'PeopleSearch Development',
            inject: 'body'
        }),
        new HtmlWebpackPlugin({
            chunks: ['helpdesk.ng'],
            filename: 'helpdesk.html',
            template: 'index.html',
            // title: 'PeopleSearch Development',
            inject: 'body'
        })
    ],
    postcss: function() {
        return [
            autoPrefixer({
                browsers: ['last 2 versions']
            })
        ];
    },
    resolve: {
        extensions: [ '', '.ts', '.js', '.json' ],
        modulesDirectories: ['./src', './vendor', 'node_modules']
    }
};
