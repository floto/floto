import notificationService from "../util/notificationService.js";

import Promise from "bluebird";
Promise.longStackTraces();

Promise.onPossiblyUnhandledRejection(function(error){
	if(error && error.suppressLog) {
		return;
	}
	notificationService.notify({
		title: 'Internal error in callback',
		text: error.message || error,
		type: 'error'
	});
});

window.addEventListener("error",(error) => {
	notificationService.notify({
		title: 'Internal error',
		text: error.message || error,
		type: 'error'
	});
});







