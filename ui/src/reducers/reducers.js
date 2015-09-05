import EventConstants from "../events/constants.js";


const reducerMap = {};

function addReducers(reducers) {
	_.extend(reducerMap, reducers);
	_.forEach(reducers, (value, key) => {
		if(!EventConstants[key]) {
			console.warn("Invalid event type: "+key);
			console.trace(value);
		}
	});
}


addReducers({
	"@@redux/INIT"() {
		// dummy method to silence warning
	},

	MANIFEST_UPDATED(state, manifest) {
		return {manifest};
	},

	FLOTO_INFO_UPDATED(state, flotoInfo) {
		return {flotoInfo};
	},

	MANIFEST_COMPILATION_STARTED(state) {
		return {serverState: _.extend({}, state.serverState, {isCompiling: true})};
	},

	MANIFEST_COMPILATION_FINISHED(state) {
		return {serverState: _.extend({}, state.serverState, {isCompiling: false})};
	},

	TASKS_UPDATED(state, tasks) {
		var activeTask = _.findWhere(tasks, {id: state.activeTaskId});
		return {tasks, activeTask}
	},

	TASK_ACTIVATED(state, taskId) {
		var activeTask = _.findWhere(state.tasks, {id: taskId});
		return {activeTask, activeTaskId: taskId};
	},

	SAFETY_CHANGED(state, safetyArmed) {
		return {clientState: _.extend({}, state.clientState, {safetyArmed})};
	}

});

// TODO: check that all event types have a reducer?

export default (state, event) => {
	let reducer = reducerMap[event.type];
	let result = state;
	if(reducer) {
		result = _.extend({}, state, reducer(state, event.payload));
	} else {
		console.warn(`Warning: No reducer registered for event type ${event.type}}`);
		console.trace("Stack trace");
	}
	return result;
}
