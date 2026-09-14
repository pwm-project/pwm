const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const { merge } = require('webpack-merge');
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
                    options: {
                        esModule: false,
                        sources: false
                    },
                    exclude: /node_modules/
                },
                {
                    test: /\.html$/,
                    use: [
                        'ngtemplate-loader',
                        {
                            loader: 'html-loader',
                            options: {
                                esModule: false
                            }
                        }
                    ],
                    exclude: /index-dev\.html$/
                },
                {
                    test: /\.(scss)$/,
                    use: [ 'style-loader', 'css-loader', {
                        loader: 'postcss-loader',
                        options: {
                            postcssOptions: {
                                plugins: [ autoPrefixer ]
                            }
                        }
                    }, 'sass-loader']
                },
                {
                    test: /\.(png|jpg|jpeg|gif|svg)$/,
                    use: [ 'url-loader?limit=25000' ]
                },
                {
                    test: [
                        require.resolve("textangular"),
                        require.resolve("textangular/dist/textAngular-sanitize")
                    ],
                    loader: "imports-loader",
                    options: {
                        imports: "side-effects angular"
                    }
                }
            ]
        },
        plugins: [
            new CopyWebpackPlugin({
                patterns: [
                    { from: 'node_modules/@microfocus/ux-ias/dist/ux-ias.css', to: 'vendor/ux-ias/' },
                    { from: 'node_modules/@microfocus/ias-icons/dist/ias-icons.css', to: 'vendor/ux-ias/' },
                    { from: 'node_modules/@microfocus/ias-icons/dist/fonts', to: 'vendor/ux-ias/fonts' },
                    { from: 'node_modules/textangular/dist/textAngular.css', to: 'vendor/textangular' }
                ]
            })
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
        return merge(commonConfig, {
            entry: {
                'peoplesearch.ng': './src/modules/peoplesearch/main',
                'helpdesk.ng': './src/modules/helpdesk/main'
            },
            optimization:{
                minimize: !disableMinimize,
                minimizer: [
                    new TerserPlugin({
                        terserOptions: {
                            compress: {warnings: false},
                            format: {comments: false}
                        },
                        extractComments: false
                    })
                ]
            }
        });
    }
    else {
        // Development-specific configuration
        return merge(commonConfig, {
            entry: {
                'peoplesearch.ng': './src/modules/peoplesearch/main',
                'helpdesk.ng': './src/modules/helpdesk/main'
            },
            plugins: [
                new HtmlWebpackPlugin({
                    chunks: ['peoplesearch.ng', 'vendor'],
                    filename: 'peoplesearch.html',
                    template: 'src/index-dev.html',
                    inject: 'body'
                }),
                new HtmlWebpackPlugin({
                    chunks: ['helpdesk.ng', 'vendor'],
                    filename: 'helpdesk.html',
                    template: 'src/index-dev.html',
                    inject: 'body'
                })
            ],
        });
    }
};
