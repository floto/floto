import Promise from "bluebird";

let urlPrefix = "/api/";

export function send(request) {
	return new Promise(function (resolve, reject) {
		var xhr = new XMLHttpRequest;
		xhr.addEventListener("error", reject);
		xhr.addEventListener("load", (result) => {
			if(!request.accept) {
				let responseJson = JSON.parse(xhr.responseText);
				resolve(responseJson);
			} else {
				resolve(xhr.responseText);
			}
		} );
		xhr.open(request.method || "GET", urlPrefix + request.url);
		xhr.setRequestHeader("Accept", request.accept || "application/json");
		xhr.send(null);
	});
}


