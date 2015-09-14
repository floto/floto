const reducerMap = {};

function addReducers(reducers) {
	_.extend(reducerMap, reducers);
}


function getShortFileName(filename) {
	return filename.replace(/^.*(\\|\/|\:)/, '');
}

addReducers({
	"@@redux/INIT"() {
		// dummy method to silence warning
	},

	MANIFEST_UPDATED(state, manifest) {
		var selectedContainer = _.findWhere(manifest.containers, {name: state.selectedContainerName});
		var templateMap = {};
		_.forEach(manifest.images, (image) => {
			let templates = [];
			templateMap[`image:${image.name}`] = templates;
			var steps = image.buildSteps;
			steps.forEach(function (buildStep) {
				if (buildStep.type === "ADD_TEMPLATE") {
					templates.push({
						name: getShortFileName(buildStep.destination),
						destination: buildStep.destination
					});
				}
			});

		});
		_.forEach(manifest.containers, (container) => {
			let imageTemplates = templateMap[`image:${container.image}`];
			let templates = [].concat(imageTemplates);
			templateMap[`container:${container.name}`] = templates;
			var steps = container.configureSteps;
			steps.forEach(function (buildStep) {
				if (buildStep.type === "ADD_TEMPLATE") {
					templates.push({
						name: getShortFileName(buildStep.destination),
						destination: buildStep.destination
					});
				}
			});

		});
		return _.extend({selectedContainer, manifest, templateMap}, mergeContainerStates(state.containerStates, manifest));
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

	CONTAINER_SELECTED(state, containerName) {
		var selectedContainer = _.findWhere(state.manifest.containers, {name: containerName});
		return {selectedContainer, selectedContainerName: containerName};
	},

	CONTAINER_STATES_UPDATED(state, containerStates) {
		return _.extend({containerStates}, mergeContainerStates(containerStates, state.manifest));
	},

	CONTAINER_FILE_SELECTED(state, selectedFile) {
		return {selectedFile, selectedFileError: null};
	},

	CONTAINER_FILE_ERROR(state, error) {
		return {selectedFileError: error, selectedFile: null};
	},

	TASKS_UPDATED(state, tasks) {
		var activeTask = _.findWhere(tasks, {id: state.activeTaskId});
		return {tasks, activeTask};
	},

	TASK_ACTIVATED(state, taskId) {
		var activeTask = _.findWhere(state.tasks, {id: taskId});
		return {activeTask, activeTaskId: taskId};
	},

	SAFETY_CHANGED(state, safetyArmed) {
		return {clientState: _.extend({}, state.clientState, {safetyArmed})};
	},

	CONFIG_UPDATED(state, config) {
		return {config, clientState: _.extend({}, state.clientState, {safetyArmed: config.armed})};
	}

});

function mergeContainerStates(containerStates = {}, manifest = {}) {
	let unmanagedContainers = [];
	var containerHash = {};
	let containers = manifest.containers || [];
	containers.forEach(function (container) {
		container.state = {status: "unknown"};
		if (!containerStates[container.name]) {
			container.state = {
				status: "unknown"
			};
			return;
		}
		container.state = containerStates[container.name];
		containerHash[container.name] = container.name;
	});

	Object.keys(containerStates).forEach(function(name) {
		if(!containerHash[name]) {
			unmanagedContainers.push({
				name: name,
				state: containerStates[name],
				unmanaged: true,
				host: "?",
				image: "?"
			});
		}
	});
	return {unmanagedContainers, containers};
}

export const eventConstants = _.indexBy(_.keys(reducerMap));

// TODO: check that all event types have a reducer?

export default (state, event) => {
	let reducer = reducerMap[event.type];
	let result = state;
	if (reducer) {
		result = _.extend({}, state, reducer(state, event.payload));
	} else {
		/*eslint-disable no-console */
		console.warn(`Warning: No reducer registered for event type ${event.type}}`);
		console.trace("Stack trace");
		/*eslint-enable no-console */
	}
	return result;
};

