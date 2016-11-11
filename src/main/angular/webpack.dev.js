var commonConfig = require('./webpack.common.js');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var webpackMerge = require('webpack-merge');

module.exports = webpackMerge(commonConfig, {
    entry: {
        'peoplesearch.ng': './src/main.dev'
    },
    module: {
        loaders: [
            {
                test: /icons\.json$/,
                loaders: ['style','css', 'fontgen?fileName=fonts/[fontname][ext]' ]
            }
        ]
    },
    plugins: [
        // Don't forget to add this to karma.conf.js
        new CopyWebpackPlugin([
            { from: 'vendor/angular-ui-router.js', to: 'vendor/' },
            { from: 'node_modules/angular/angular.js', to: 'vendor/' },
            { from: 'node_modules/angular-sanitize/angular-sanitize.js', to: 'vendor/' },
            { from: 'node_modules/angular-translate/dist/angular-translate.js', to: 'vendor/' },
            { from: 'images/avatars', to: 'images/avatars' }
        ])
    ]
});
