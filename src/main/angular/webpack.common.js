var CopyWebpackPlugin = require('copy-webpack-plugin');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var path = require('path');
var webpack = require('webpack');

var outDir = path.resolve(__dirname, 'dist');

module.exports = {
    devServer: {
        outputPath: outDir
    },
    devtool: 'cheap-module-source-map',
    // Externals copied to /dist via CopyWebpackPlugin
    externals:
    {
        'angular': true,
        // Wrapped in window because of hyphens
        'angular-ui-router': 'window["angular-ui-router"]'
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
                loaders: [ 'style', 'css', 'sass' ]
            },
            {
                test: /\.json/,
                loaders: [ 'json' ]
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
            template: 'index.html',
            inject: 'body'
        }),

        new CopyWebpackPlugin([
            { from: 'vendor/angular-ui-router.js', to: 'vendor/' },
            { from: 'node_modules/angular/angular.js', to: 'vendor/' }
        ])
    ],
    resolve: {
        extensions: [ '', '.ts', '.js', '.json' ],
        modulesDirectories: ['./src', './vendor', 'node_modules']
    }
};
