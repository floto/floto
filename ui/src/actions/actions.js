import * as rest from "../util/rest.js";
import EventConstants from "../events/constants.js";

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
	rest.send({method: "POST", url: "manifest/compile"}).then((result) => {
		refreshManifest(dispatch);
	});
}
