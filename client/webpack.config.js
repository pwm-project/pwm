const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const UglifyJsPlugin = require('uglifyjs-webpack-plugin');
const webpackMerge = require('webpack-merge');
const webpack = require('webpack');
const autoPrefixer = require('autoprefixer');

const outDir = path.resolve(__dirname, 'dist');
const srcDir = path.resolve(__dirname, 'src');

module.exports = function (env, argv) {
    const isProductionMode = (argv["mode"] === "production");
    const disableMinimize = (env && env.disableMinimize) || false;

    const commonConfig = {
        devtool: 'source-map',
        entry: {
            'changepassword.ng': './src/modules/changepassword/changepassword.module',
            'configeditor.ng': './src/modules/configeditor/configeditor.module'

            // (see production and development specific sections below for more entries)
        },
        output: {
            filename: "[name].js",
            path: outDir
        },
        resolve: {
            extensions: [".ts", ".js"]
        },
        module: {
            rules: [
                {
                    test: /.ts$/,
                    loader: "ts-loader"
                },
                {
                    test: /\.ts$/,
                    enforce: 'pre',
                    loader: 'tslint-loader'
                },
                {
                    test: /index-dev\.html$/,
                    loader: 'html-loader',
                    exclude: /node_modules/
                },
                {
                    test: /\.html$/,
                    loader: 'ngtemplate-loader!html-loader',
                    exclude: /index-dev\.html$/
                },
                {
                    test: /\.(scss)$/,
                    loaders: [ 'style-loader', 'css-loader', 'sass-loader', {
                        loader: 'postcss-loader',
                        options: {
                            plugins: function () {
                                return [autoPrefixer('last 2 versions')]
                            }
                        }
                    }]
                },
                {
                    test: /\.(png|jpg|jpeg|gif|svg)$/,
                    loaders: [ 'url-loader?limit=25000' ]
                },
                {
                    test: [
                        require.resolve("textangular"),
                        require.resolve("textangular/dist/textAngular-sanitize")
                    ],
                    use: "imports-loader?angular"
                }
            ]
        },
        plugins: [
            new CopyWebpackPlugin([
                { from: 'node_modules/@microfocus/ux-ias/dist/ux-ias.css', to: 'vendor/ux-ias/' },
                { from: 'node_modules/@microfocus/ias-icons/dist/ias-icons.css', to: 'vendor/ux-ias/' },
                { from: 'node_modules/@microfocus/ias-icons/dist/fonts', to: 'vendor/ux-ias/fonts' },
                { from: 'node_modules/textangular/dist/textAngular.css', to: 'vendor/textangular' }
            ])
        ],
        optimization: {
            splitChunks: {
                cacheGroups: {
                    vendor: {
                        test: /[\\/]node_modules[\\/]/,
                        name: "vendor",
                        chunks: "all"
                    }
                }
            }
        }
    };

    if (isProductionMode) {
        // Production-specific configuration
        return webpackMerge(commonConfig, {
            entry: {
                'peoplesearch.ng': './src/modules/peoplesearch/main',
                'helpdesk.ng': './src/modules/helpdesk/main'
            },
            optimization:{
                minimize: !disableMinimize,
                minimizer: [
                    new UglifyJsPlugin({
                        sourceMap: true,
                        uglifyOptions: {
                            compress: {warnings: false},
                            comments: false
                        }
                    })
                ]
            }
        });
    }
    else {
        // Development-specific configuration
        return webpackMerge(commonConfig, {
            entry: {
                'peoplesearch.ng': './src/modules/peoplesearch/main',
                'helpdesk.ng': './src/modules/helpdesk/main'
            },
            plugins: [
                new HtmlWebpackPlugin({
                    chunks: ['peoplesearch.ng', 'vendor'],
                    chunksSortMode: 'dependency',
                    filename: 'peoplesearch.html',
                    template: 'src/index-dev.html',
                    inject: 'body',
                    livereload: true
                }),
                new HtmlWebpackPlugin({
                    chunks: ['helpdesk.ng', 'vendor'],
                    chunksSortMode: 'dependency',
                    filename: 'helpdesk.html',
                    template: 'src/index-dev.html',
                    inject: 'body',
                    livereload: true
                })
            ],
        });
    }
};
