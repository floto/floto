import * as rest from "../util/rest.js";
import EventConstants from "../events/constants.js";
import notificationService from "../util/notificationService.js";


export function updateManifest(dispatch, manifest) {
	dispatch({
		type: EventConstants.MANIFEST_UPDATED,
		payload: manifest
	});
}



export function refreshManifest(dispatch) {
	rest.send({method: "GET", url: "manifest"}).then((result) => {
		updateManifest(dispatch, result);
	});
}

export function recompileManifest(dispatch) {
	notificationService.notify({
		title: 'Recompiling',
		text: 'Now recompiling.'
	});
	rest.send({method: "POST", url: "manifest/compile"}).then((result) => {
		refreshManifest(dispatch);
	});
}
