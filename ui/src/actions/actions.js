import * as rest from "../util/rest.js";
import notificationService from "../util/notificationService.js";
import taskService from "../tasks/taskService.js";

import PatchCreationDialog from "../patches/PatchCreationDialog";

export function loadContainerStates(store) {
	rest.send({method: "GET", url: "containers/_state"}).then((result) => {
		store.dispatch({
			type: "CONTAINER_STATES_UPDATED",
			payload: result.states
		});
	});
}

export function loadHostStates(store) {
	rest.send({method: "GET", url: "hosts/_state"}).then((result) => {
		store.dispatch({
			type: "HOST_STATES_UPDATED",
			payload: result.states
		});
	});
}
export function loadTasks(store) {
	rest.send({method: "GET", url: "tasks"}).then((tasks) => {
		return store.dispatch({
			type: "TASKS_UPDATED",
			payload: tasks
		});
	});
}

export function loadFile(store, url, fileName) {
	rest.send({method: "GET", url, accept: "*"}).then((content) => {
		store.dispatch({
			type: "FILE_SELECTED",
			payload: {fileName: encodeURIComponent(fileName), content}
		});
	}).catch((error) => {
		store.dispatch({
			type: "FILE_ERROR",
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

export function restartContainers(store, containerNames) {
	taskService.httpPost(store, "containers/_restart", {containers: containerNames})
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
			loadHostStates(store);
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
		store.dispatch({type: "FLOTO_INFO_UPDATED", payload: info});

	});
}

export function recompileManifest(store) {
	store.dispatch({type: "MANIFEST_COMPILATION_STARTED"});
	return taskService.httpPost(store, "manifest/compile").then(() => {
		return null;
	}).finally(() => {
		store.dispatch({type: "MANIFEST_COMPILATION_FINISHED"});
		refreshManifest(store);
		let state = store.getState();
		if (state.selectedContainerName && state.selectedFile) {
			loadContainerFile(store, state.selectedContainerName, decodeURIComponent(state.selectedFile.fileName));
		}
		if (state.selectedHostName && state.selectedFile) {
			loadHostFile(store, state.selectedHostName, decodeURIComponent(state.selectedFile.fileName));
		}
		return null;
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
		return null;
	}).catch((error) => {
		store.dispatch({
			type: "MANIFEST_ERROR_UPDATED",
			payload: error
		});
		return null;
	}).finally(() => {
		loadContainerStates(store);
	});
}


export function updateManifest(store, manifest) {
	store.dispatch({
		type: "MANIFEST_UPDATED",
		payload: manifest
	});
}

export function changeSafety(store, safetyArmed) {
	store.dispatch({type: "SAFETY_CHANGED", payload: safetyArmed});
}


export function loadPatches(store) {
	rest.send({method: "GET", url: "patches"}).then((result) => {
		store.dispatch({type: "PATCHES_LOADED", payload: result});
	});
}

export function loadPatchInfo(store, patchId) {
	store.dispatch({type: "PATCH_SELECTED", payload: patchId});
	rest.send({method: "GET", url: `patches/${patchId}/patchInfo`}).then((result) => {
		store.dispatch({type: "PATCH_INFO_LOADED", payload: result});
	});
}


export function createFullPatch(store) {
	createPatch(store, null);
}


function createPatch(store, parentPatchId) {
	var elementById = document.getElementById("dialog");
	let done = (patchProperties) => {
		setTimeout(() => {
			React.unmountComponentAtNode(elementById);
		}, 0);
		if (!patchProperties) {
			return;
		}
		patchProperties.parentPatchId = parentPatchId;
		taskService.httpPost(store, "patches/create", patchProperties).then(() => {
			loadPatches(store);
		});
	};
	React.render(<PatchCreationDialog done={done} show={true}/>, elementById);
}

export function createIncrementalPatch(store, parentPatchId) {
	createPatch(store, parentPatchId);
}

export function activatePatch(store, patchId) {
	taskService.httpPost(store, `patches/${patchId}/activate`).then(() => {
		loadPatches(store);
		refreshManifest(store);
	});
}


export function uploadPatch(store, patchFile) {
	taskService.httpPost(store, "patches/upload/" + patchFile.name, {blob: patchFile}, {
			uploadProgressFn: (progress) => {
				store.dispatch({type: "PATCH_UPLOAD_PROGRESSED", payload: progress});
			}
		})
		.finally(() => {
			store.dispatch({type: "PATCH_UPLOAD_COMPLETED", payload: null});
			loadPatches(store);
		});
}


export function downloadPatch(store, patchId) {
	var a = document.createElement('a');
	document.body.appendChild(a);
	a.download = patchId + ".floto-patch.zip";
	a.href = "/api/patches/"+patchId+"/download";
	a.click();
	document.body.removeChild(a);
}






