import { createStore, combineReducers, compose, applyMiddleware } from 'redux'
import { Provider } from 'react-redux'
import { Redirect, Router, Route } from 'react-router'
import { history } from 'react-router/lib/HashHistory';

import NavigationBar from "./NavigationBar.js"
import Application from "./Application"

import Containers from "../containers/Containers"
import Hosts from "../hosts/Hosts"
import Tasks from "../tasks/Tasks"
import Task from "../tasks/Task"
import Manifest from "../manifest/Manifest"

import reducers from '../reducers/reducers';

import * as actions from "../actions/actions";

import EventConstants from "../events/constants.js";

var initialState = {
	manifest: {},
	serverState: {},
	clientState: {
		safetyArmed: true
	}
};
const store = createStore(reducers, initialState);

if (module.hot) {
	// Enable Webpack hot module replacement for reducers
	module.hot.accept('../reducers/reducers', () => {
		const nextRootReducer = require('../reducers/reducers');
		store.replaceReducer(nextRootReducer);
	});
}

let routes = () => {
	return <Router history={history}>
		<Redirect from="/" to="/containers"/>
		<Route component={Application}>
			<Route path="/containers" component={Containers}/>
			<Route path="/hosts" component={Hosts}/>
			<Route path="tasks" component={Tasks}>
				<Route path=":taskId" component={Task} onEnter={
				(nextState, transition)=>{
					store.dispatch({type: EventConstants.TASK_ACTIVATED, payload: nextState.params.taskId});
				}}/>
			</Route>
			<Route path="/manifest" component={Manifest}/>
		</Route>
	</Router>
};
export default React.createClass({
	render() {
		return <div style={{}}>
			<Provider key="provider" store={store}>
				{routes}
			</Provider>
		</div>;
	}
});
window.addEventListener("load", function () {
	actions.refreshManifest(store.dispatch);
});

