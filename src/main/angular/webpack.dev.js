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
    },
    plugins: [
        // Because we copy the output to another directory, we need file system watch support.
        // Webpack-dev-server does not do this without the plugin.
        new WriteFileWebpackPlugin()
    ]
});
