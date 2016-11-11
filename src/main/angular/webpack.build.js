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
                loaders: ['style','css', 'fontgen?fileName=fonts/[fontname]-[hash][ext]' ]
            }
        ]
    }
});
