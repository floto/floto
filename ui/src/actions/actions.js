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




export function refreshManifest(dispatch) {
	rest.send({method: "GET", url: "manifest"}).then((result) => {
		updateManifest(dispatch, result);
	});
}

export function recompileManifest(dispatch) {
	dispatch({type: EventConstants.MANIFEST_COMPILATION_STARTED});
	taskService.httpPost(dispatch, "manifest/compile").then(() => {
		console.log("DONE COMPILING");
		refreshManifest(dispatch);
	}).finally(() => {
		dispatch({type: EventConstants.MANIFEST_COMPILATION_FINISHED})
	});
}
