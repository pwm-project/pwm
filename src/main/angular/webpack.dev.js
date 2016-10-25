var commonConfig = require('./webpack.common.js');
var path = require('path');
var webpackMerge = require('webpack-merge');
var WriteFileWebpackPlugin = require('write-file-webpack-plugin');

var outDir = path.resolve(__dirname, 'dist');

module.exports = webpackMerge(commonConfig, {
    devServer: {
        contentBase: outDir,
        port: 4000
    },
    entry: {
        'peoplesearch.ng': './src/main.dev'
    }
});
