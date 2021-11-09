const path = require('path');
const webpack = require('webpack')
const TerserPlugin = require("terser-webpack-plugin");
const ESLintPlugin = require('eslint-webpack-plugin');
const CopyPlugin = require("copy-webpack-plugin");

module.exports = {
 entry: {
    "jatos": "./javascripts/entry.js"
  },
  output: {
    publicPath: "jatos-publix/javascripts/",
    path: path.resolve(__dirname, 'public/javascripts'),
    filename: "[name].js"
  },
  optimization: {
    minimize: true,
    minimizer: [new TerserPlugin()],
  },
  devtool: "source-map",
  mode: 'production',
  plugins: [
    new webpack.ProvidePlugin({
      "jquery": "./jquery-3.5.1.min.js"
    }),
    new ESLintPlugin({
      "files": ["./javascripts/jatos.js", "./javascripts/heartbeat.js", "./javascripts/httpLoop.js"]
    }),
    new CopyPlugin({
      patterns: [
        { from: "./javascripts/jquery.ajax-retry.min.js", to: "jquery.ajax-retry.min.js" }
      ],
    }),
  ],
  externals: {
    "jQuery": "jatos.jQuery"
  }
};

