import Promise from "bluebird";

let urlPrefix = "/api/";

export function send(request) {
	return new Promise(function (resolve, reject) {
		var xhr = new XMLHttpRequest;
		xhr.addEventListener("error", reject);
		xhr.addEventListener("load", (result) => {
			if (xhr.status >= 200 && xhr.status < 300) {
				if (!request.accept) {
					let responseJson = JSON.parse(xhr.responseText);
					resolve(responseJson);
				} else {
					resolve(xhr.responseText);
				}
			} else {
				try {
					let responseJson = JSON.parse(xhr.responseText);
					reject(responseJson);
				} catch(ignored) {
					reject(xhr.responseText);
				}

			}
		});
		xhr.open(request.method || "GET", urlPrefix + request.url);
		xhr.setRequestHeader("Accept", request.accept || "application/json");
		xhr.setRequestHeader("Content-Type", "application/json");
		if (request.request) {
			xhr.send(JSON.stringify(request.request));
		} else {
			xhr.send();
		}
	});
}





