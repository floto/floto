import * as rest from "../util/rest.js";
import EventConstants from "../events/constants.js";
import notificationService from "../util/notificationService.js";
import taskService from "../tasks/taskService.js";

export function updateManifest(dispatch, manifest) {
	dispatch({
		type: EventConstants.MANIFEST_UPDATED,
		payload: manifest
	});
}


export function loadTasks(dispatch) {
	rest.send({method: "GET", url: "tasks"}).then((tasks) => {
		dispatch({
			type: EventConstants.TASKS_UPDATED,
			payload: tasks
		});
	});
}

export function loadFile(dispatch, containerName, fileName) {
	// Fixup template path to handle double slash
	fileName = fileName.replace("template//", "template/%2F");
	rest.send({method: "GET", url: `containers/${containerName}/${(fileName)}`, accept: "*"}).then((file) => {
		console.log(file);
		dispatch({
			type: EventConstants.CONTAINER_FILE_SELECTED,
			payload: file
		});
	});
}

export function redeployContainers(dispatch, containerNames, deploymentMode) {
	console.log(`Redeploy ${containerNames} ${deploymentMode}`);
	taskService.httpPost(dispatch, "containers/_redeploy", {containers: containerNames, deploymentMode}).then(() => {
//		refreshManifest(dispatch);
	}).finally(() => {
//		dispatch({type: EventConstants.MANIFEST_COMPILATION_FINISHED})
	});

}



export function refreshManifest(dispatch) {
	rest.send({method: "GET", url: "manifest"}).then((manifest) => {
		updateManifest(dispatch, manifest);
		let title = "floto - " + (manifest.site.projectName || manifest.site.domainName);
		if(manifest.site.environment) {
			title += " (" + manifest.site.environment + ")";
		}
		document.title = title;

	});
}

export function getFlotoInfo(dispatch) {
	rest.send({method: "GET", url: "info"}).then((info) => {
		dispatch({type: EventConstants.FLOTO_INFO_UPDATED, payload: info});

	});
}

export function recompileManifest(dispatch) {
	dispatch({type: EventConstants.MANIFEST_COMPILATION_STARTED});
	taskService.httpPost(dispatch, "manifest/compile").then(() => {
		refreshManifest(dispatch);
	}).finally(() => {
		dispatch({type: EventConstants.MANIFEST_COMPILATION_FINISHED})
	});
}


export function changeSafety(dispatch, safetyArmed) {
	dispatch({type: EventConstants.SAFETY_CHANGED, payload: safetyArmed});
}
