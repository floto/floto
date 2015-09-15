import * as rest from "../util/rest.js";
import EventConstants from "../events/constants.js";
import notificationService from "../util/notificationService.js";
import taskService from "../tasks/taskService.js";


export function loadContainerStates(store) {
	rest.send({method: "GET", url: "containers/_state"}).then((result) => {
		store.dispatch({
			type: EventConstants.CONTAINER_STATES_UPDATED,
			payload: result.states
		});
	});
}

export function loadHostStates(store) {
	rest.send({method: "GET", url: "hosts/_state"}).then((result) => {
		/* TODO
		store.dispatch({
			type: EventConstants.CONTAINER_STATES_UPDATED,
			payload: result.states
		});
		*/
	});
}
export function loadTasks(store) {
	rest.send({method: "GET", url: "tasks"}).then((tasks) => {
		store.dispatch({
			type: EventConstants.TASKS_UPDATED,
			payload: tasks
		});
	});
}

export function loadFile(store, url, fileName) {
	rest.send({method: "GET", url, accept: "*"}).then((content) => {
		store.dispatch({
			type: EventConstants.FILE_SELECTED,
			payload: {fileName: encodeURIComponent(fileName), content}
		});
	}).catch((error) => {
		store.dispatch({
			type: EventConstants.FILE_ERROR,
			payload: {fileName: encodeURIComponent(fileName), error}
		});
	});
}

export function loadContainerFile(store, containerName, fileName) {
	// Fixup template path to handle double slash
	fileName = fileName.replace("template//", "template/%2F");
	loadFile(store, `containers/${containerName}/${(fileName)}`, fileName);
}

export function loadHostFile(store, hostName, fileName) {
	// Fixup template path to handle double slash
	fileName = fileName.replace("template//", "template/%2F");
	loadFile(store, `hosts/${hostName}/${(fileName)}`, fileName);
}

export function redeployContainers(store, containerNames, deploymentMode) {
	taskService.httpPost(store, "containers/_redeploy", {containers: containerNames, deploymentMode})
		.finally(() => {
			loadContainerStates(store);
		});

}


export function startContainers(store, containerNames) {
	taskService.httpPost(store, "containers/_start", {containers: containerNames})
		.finally(() => {
			loadContainerStates(store);
		});
}

export function stopContainers(store, containerNames) {
	taskService.httpPost(store, "containers/_stop", {containers: containerNames})
		.finally(() => {
			loadContainerStates(store);
		});
}

export function purgeContainerData(store, containerNames) {
	taskService.httpPost(store, "containers/_purgeData", {containers: containerNames});
}


export function destroyContainers(store, containerName, hostName) {
	taskService.httpPost(store, "containers/_destroyUnmanaged", {containerName, hostName})
		.finally(() => {
			loadContainerStates(store);
		});
}

export function redeployHosts(store, hostNames) {
	taskService.httpPost(store, "hosts/_redeploy", {hosts: hostNames})
		.finally(() => {
			loadContainerStates(store);
		});
}

export function startHosts(store, hostNames) {
	taskService.httpPost(store, "hosts/_start", {hosts: hostNames})
		.finally(() => {
			loadContainerStates(store);
		});
}

export function stopHosts(store, hostNames) {
	taskService.httpPost(store, "hosts/_stop", {hosts: hostNames})
		.finally(() => {
			loadContainerStates(store);
		});
}

export function destroyHosts(store, hostNames) {
	taskService.httpPost(store, "hosts/_delete", {hosts: hostNames})
		.finally(() => {
			loadContainerStates(store);
		});
}



export function getFlotoInfo(store) {
	rest.send({method: "GET", url: "info"}).then((info) => {
		store.dispatch({type: EventConstants.FLOTO_INFO_UPDATED, payload: info});

	});
}

export function recompileManifest(store) {
	store.dispatch({type: EventConstants.MANIFEST_COMPILATION_STARTED});
	taskService.httpPost(store, "manifest/compile").then(() => {
		refreshManifest(store);
		let state = store.getState();
		if (state.selectedContainerName && state.selectedFile) {
			loadContainerFile(store, state.selectedContainerName, decodeURIComponent(state.selectedFile.fileName));
		}
		if (state.selectedHostName && state.selectedFile) {
			loadHostFile(store, state.selectedHostName, decodeURIComponent(state.selectedFile.fileName));
		}
	}).finally(() => {
		store.dispatch({type: EventConstants.MANIFEST_COMPILATION_FINISHED});
	});
}


export function refreshManifest(store) {
	rest.send({method: "GET", url: "manifest"}).then((manifest) => {
		updateManifest(store, manifest);
		let title = "floto - " + (manifest.site.projectName || manifest.site.domainName);
		if (manifest.site.environment) {
			title += " (" + manifest.site.environment + ")";
		}
		document.title = title;

	});
}


export function updateManifest(store, manifest) {
	store.dispatch({
		type: EventConstants.MANIFEST_UPDATED,
		payload: manifest
	});
}

export function changeSafety(store, safetyArmed) {
	store.dispatch({type: EventConstants.SAFETY_CHANGED, payload: safetyArmed});
}


export function loadPatches(store) {
	rest.send({method: "GET", url: "patches"}).then((result) => {
		store.dispatch({type: "PATCHES_LOADED", payload: result.patches});
	});
}





