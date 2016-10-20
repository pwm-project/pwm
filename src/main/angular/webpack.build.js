var commonConfig = require('./webpack.common.js');
var webpackMerge = require('webpack-merge');

module.exports = webpackMerge(commonConfig, {
    entry: {
        'app': './src/main'
    }
});
