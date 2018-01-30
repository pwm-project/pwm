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


var commonConfig = require('./webpack.common.js');
var webpack = require('webpack');
var webpackMerge = require('webpack-merge');

module.exports = webpackMerge(commonConfig, {
    devtool: 'source-map',
    entry: {
        'peoplesearch.ng': './src/main',
        'changepassword.ng': './src/pages/changepassword/changepassword.module',
        'configeditor.ng': './src/pages/configeditor/configeditor.module'
    },
    module: {
        loaders: [
            {
                test: /icons\.json$/,
                loaders: [
                    'ignore',
                    // Need to output to an external file, since fontgen consolidates: "../.." into: "../", even after
                    // doing the string-replace below.  This is a problem, since we're loading from a webjar now.
                    'file?name=fonts.css',
                    // This replaces path from app root so the urls are relative to the output directory
                    'string-replace?search=url%5C("%5C/&replace=url("./&flags=gm',
                    'fontgen?fileName=fonts/[fontname]-[hash][ext]'
                ]
            }
        ]
    },
    plugins: [
        new webpack.optimize.UglifyJsPlugin({
            compress: { warnings: false },
            comments: false,
            sourceMap: true
        })
    ]
});
