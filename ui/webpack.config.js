var path = require('path');
var webpack = require("webpack");

module.exports = {
	// This is the main file that should include all other JS files
	context: path.resolve(__dirname, "src"),
	entry: [
//    'webpack-dev-server/client?http://localhost:8081', // WebpackDevServer host and port
//    'webpack/hot/only-dev-server',
		"./main.js"],
	target: "web",
	debug: true,
	devtool: 'eval',
	output: {
		path: __dirname + "/target/bundle",
		publicPath: "/",
		// If you want to generate a filename with a hash of the content (for cache-busting)
		// filename: "main-[hash].js",
		filename: "bundle.js",
		chunkFilename: "[chunkhash].js"
	},
	resolve: {
		modulesDirectories: ['bower_components', 'node_modules']
	},
	module: {
		loaders: [
			{test: /\.js$/, loaders: ["babel"], include:[path.join(__dirname, 'src'), path.join(__dirname, 'node_modules', 'react-router-bootstrap')]},
			{test: /\.css$/, loader: "style!css"},
			{test: /\.svg$/, loader: "file-loader"},
			{
				test: /\.(otf|eot|svg|ttf|woff|woff2)(\?.+)?$/,
				loader: 'url-loader?limit=8192'
			}
		]
	},
	plugins: [
		new webpack.ProvidePlugin({
			React: "react",
			ReactDOM: "react-dom",
			_: "lodash",
//			$: "jquery",
//			jQuery: "jquery"
		})
//    new webpack.HotModuleReplacementPlugin(),
//    new webpack.NoErrorsPlugin()
	]
};
