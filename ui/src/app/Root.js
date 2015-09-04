import { createStore, combineReducers, compose, applyMiddleware } from 'redux'
import { Provider } from 'react-redux'
import { Redirect, Router, Route } from 'react-router'
import { history } from 'react-router/lib/HashHistory';

import NavigationBar from "./NavigationBar.js"
import Application from "./Application"

import Containers from "../containers/Containers"
import Hosts from "../hosts/Hosts"
import Tasks from "../tasks/Tasks"

import reducers from '../reducers/reducers';

import * as actions from "../actions/actions";

const store = createStore(reducers, {manifest: {}});

if (module.hot) {
	// Enable Webpack hot module replacement for reducers
	module.hot.accept('../reducers/reducers', () => {
		const nextRootReducer = require('../reducers/reducers');
		store.replaceReducer(nextRootReducer);
	});
}

let routes = () => {
	return <Router history={history}>
		<Redirect from="/" to="/containers" />
		<Route component={Application}>
			<Route path="/containers" component={Containers} />
			<Route path="/hosts" component={Hosts} />
			<Route path="/tasks" component={Tasks} />
		</Route>
	</Router>
};
export default React.createClass({
	render() {
		return <div style={{position: "absolute", width: "100%", top: 0, bottom: 80}}>
			<Provider key="provider" store={store}>
				{routes}
			</Provider>
		</div>;
	}
});
window.addEventListener("load", function () {
	actions.refreshManifest(store.dispatch);
});

