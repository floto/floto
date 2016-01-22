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
		var selectedHost = _.findWhere(manifest.hosts, {name: state.selectedHostName});
		var templateMap = {};

		let collectTemplates = (steps, templates) => {
			steps.forEach(function (buildStep) {
				if (buildStep.type === "ADD_TEMPLATE") {
					templates.push({
						name: getShortFileName(buildStep.destination),
						destination: buildStep.destination
					});
				}
			});

		};
		_.forEach(manifest.images, (image) => {
			let templates = [];
			templateMap[`image:${image.name}`] = templates;
			collectTemplates(image.buildSteps, templates);

		});
		_.forEach(manifest.containers, (container) => {
			let imageTemplates = templateMap[`image:${container.image}`];
			let templates = [].concat(imageTemplates);
			templateMap[`container:${container.name}`] = templates;
			collectTemplates(container.configureSteps, templates);
		});
		_.forEach(manifest.hosts, (host) => {
			let templates = [];
			templateMap[`host:${host.name}`] = templates;
			collectTemplates(host.postDeploySteps, templates);
		});
		var newState = _.extend({
			manifestError: null,
			selectedContainer,
			selectedHost,
			manifest,
			templateMap
		}, mergeContainerStates(state.containerStates, manifest), mergeHostStates(state.hostStates, state.manifest));
		return newState;
	},

	MANIFEST_ERROR_UPDATED(state, manifestError) {
		return {manifestError};
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

	HOST_SELECTED(state, hostName) {
		var selectedHost = _.findWhere(state.manifest.hosts, {name: hostName});
		return {selectedHost, selectedHostName: hostName};
	},

	CONTAINER_STATES_UPDATED(state, containerStates) {
		return _.extend({containerStates}, mergeContainerStates(containerStates, state.manifest));
	},

	HOST_STATES_UPDATED(state, hostStates) {
		return _.extend({hostStates}, mergeHostStates(hostStates, state.manifest));
	},

	FILE_SELECTED(state, selectedFile) {
		return {selectedFile, selectedFileError: null};
	},

	FILE_ERROR(state, error) {
		return {selectedFileError: error, selectedFile: null};
	},

	TASKS_UPDATED(state, tasks) {
		var activeTask = _.findWhere(tasks, {id: state.activeTaskId});
		return {tasks, activeTask};
	},

	TASK_SELECTED(state, taskId) {
		var activeTask = _.findWhere(state.tasks, {id: taskId});
		return {activeTask, activeTaskId: taskId};
	},

	SAFETY_CHANGED(state, safetyArmed) {
		return {clientState: _.extend({}, state.clientState, {safetyArmed})};
	},

	CONFIG_UPDATED(state, config) {
		if (config.defaultDeploymentMode === "fromRootImage" && !config.canDeployFromRootImage) {
			config.defaultDeploymentMode = "fromBaseImage";
		}
		return {config, clientState: _.extend({}, state.clientState, {safetyArmed: config.armed})};
	},

	PATCHES_LOADED(state, patchesInfo) {
		return {patches: patchesInfo.patches, activePatchId: patchesInfo.activePatchId};
	},


	PATCH_SELECTED(state, selectedPatchId) {
		return {selectedPatchId};
	},

	PATCH_INFO_LOADED(state, selectedPatch) {
		return {selectedPatch};
	},

	PATCH_UPLOAD_PROGRESSED(state, uploadProgress) {
		return {uploadProgress};
	},

	PATCH_UPLOAD_COMPLETED(state, progress) {
		return {uploadProgress: null};
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

	Object.keys(containerStates).forEach(function (name) {
		if (!containerHash[name]) {
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

function mergeHostStates(hostStates = {}, manifest = {}) {
	let hosts = manifest.hosts || [];
	hosts = hosts.map(function (host) {
		host.state = {status: "unknown"};
		if (!hostStates[host.name]) {
			host.state = {
				status: "unknown"
			};
			return;
		}
		host.state = hostStates[host.name];
		return host;
	});
	return {hosts};
}


export const eventConstants = _.sortedIndexBy(_.keys(reducerMap));

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

