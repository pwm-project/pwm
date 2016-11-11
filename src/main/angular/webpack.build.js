var commonConfig = require('./webpack.common.js');
var webpackMerge = require('webpack-merge');

module.exports = webpackMerge(commonConfig, {
    entry: {
        'peoplesearch.ng': './src/main'
    },
    module: {
        loaders: [
            {
                test: /icons\.json$/,
                loaders: [
                    'style',
                    'raw',
                    // This replaces path from app root so the urls are relative to the output directory
                    'string-replace?search=url%5C("%5C/&replace=url("./&flags=gm',
                    'fontgen?fileName=fonts/[fontname]-[hash][ext]'
                ]
            }
        ]
    }
});
