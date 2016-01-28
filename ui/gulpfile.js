'use strict';

var gulp = require('gulp');
var gutil = require("gulp-util");
var eslint = require('gulp-eslint');
var webpack = require('webpack');
var WebpackDevServer = require("webpack-dev-server");

var webpackConfig = require("./webpack.config.js");

gulp.task('default', ['dev']);


gulp.task('dev', function () {
	var devConfig = Object.create(webpackConfig);
	devConfig.plugins.push(new webpack.HotModuleReplacementPlugin());
	devConfig.plugins.push(new webpack.NoErrorsPlugin());
	devConfig.entry.unshift('webpack/hot/only-dev-server');
	devConfig.entry.unshift('webpack-dev-server/client?http://localhost:8081');

	var compiler = webpack(devConfig);
	var server = new WebpackDevServer(compiler, {
		// webpack-dev-server options
		contentBase: "./src/",
		// or: contentBase: "http://localhost/",

		hot: true,
		// Enable special support for Hot Module Replacement
		// Page is no longer updated, but a "webpackHotUpdate" message is send to the content
		// Use "webpack/hot/dev-server" as additional module in your entry point
		// Note: this does _not_ add the `HotModuleReplacementPlugin` like the CLI option does.

		// webpack-dev-middleware options
		quiet: false,
		noInfo: false,
		lazy: false,
		filename: "bundle.js",
		watchOptions: {
			aggregateTimeout: 300
		},
		publicPath: "/",
		stats: {
			colors: true,
			chunks: false,
			modules: false

		},

		// Set this as true if you want to access dev server from arbitrary url.
		// This is handy if you are using a html5 router.
//    historyApiFallback: false,

		// Set this if you want webpack-dev-server to delegate a single path to an arbitrary server.
		// Use "*" to proxy all paths to the specified server.
		// This is useful if you want to get rid of 'http://localhost:8080/' in script[src],
		// and has many other use cases (see https://github.com/webpack/webpack-dev-server/pull/127 ).
		proxy: {
			"/api/*": "http://localhost:40004"
		}
	});
	server.listen(8081, "0.0.0.0", function () {
		console.log("listening");
	});
});

gulp.task("production", [], function (callback) {
	// run webpack
	process.env.NODE_ENV = 'production';

	var productionConfig = Object.create(webpackConfig);
	productionConfig.devtool = "#source-map";
	productionConfig.plugins.push(new webpack.DefinePlugin({
		"process.env": {
			NODE_ENV: JSON.stringify("production")
		}
	}));
	/*productionConfig.plugins.push(new webpack.optimize.UglifyJsPlugin({
	 mangle: false
	 }));
	 */
	webpack(productionConfig, function (err, stats) {
		if (err) throw new gutil.PluginError("webpack:build-dev", err);
		gutil.log("[production]", stats.toString({
			colors: true
		}));
		callback();
	});
});

gulp.task('lint', function () {
	return gulp.src(['src/**/*.js'])
		// eslint() attaches the lint output to the eslint property
		// of the file object so it can be used by other modules.
		.pipe(eslint())
		// eslint.format() outputs the lint results to the console.
		// Alternatively use eslint.formatEach() (see Docs).
		.pipe(eslint.format());
});
