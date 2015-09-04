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

	TASKS_UPDATED(state, tasks) {
		return {tasks}
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
