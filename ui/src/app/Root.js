import { createStore, combineReducers, compose, applyMiddleware } from 'redux'
import { Provider } from 'react-redux'
import { Redirect, Router, Route } from 'react-router'
import { history } from 'react-router/lib/HashHistory';

import NavigationBar from "./NavigationBar.js"
import Application from "./Application"

import Containers from "../containers/Containers"
import Container from "../containers/Container"
import ContainerFile from "../containers/ContainerFile"

import Hosts from "../hosts/Hosts"
import Tasks from "../tasks/Tasks"
import Task from "../tasks/Task"
import Manifest from "../manifest/Manifest"

import reducers from '../reducers/reducers';

import * as actions from "../actions/actions";

import EventConstants from "../events/constants.js";

var initialState = {
	manifest: {},
	templateMap: {},
	serverState: {},
	clientState: {
		safetyArmed: true
	},
	flotoInfo: {}
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
			<Route path="/containers" component={Containers}>
				<Route path=":containerName" component={Container} onEnter={
				(nextState, transition)=>{
					store.dispatch({type: EventConstants.CONTAINER_SELECTED, payload: nextState.params.containerName});
					// workaround for file loading
					if(nextState.params.splat) {
						actions.loadFile(store.dispatch, nextState.params.containerName, nextState.params.splat)
					}
				}}>
					<Route path="file/*" component={ContainerFile} onEnter={
				(nextState, transition)=>{
					actions.loadFile(store.dispatch, nextState.params.containerName, nextState.params.splat)
				}}/>
				</Route>
			</Route>
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
	actions.getFlotoInfo(store.dispatch);
});

