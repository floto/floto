import notificationService from "../util/notificationService.js";

import Promise from "bluebird";
Promise.longStackTraces();

Promise.onPossiblyUnhandledRejection(function(error){
	notificationService.notify({
		title: 'Internal error',
		text: error,
		type: 'error'
	});
	throw error;
});

window.onerror =  (error,b ) => {
	notificationService.notify({
		title: 'Internal error',
		text: error,
		type: 'error'
	});
};



