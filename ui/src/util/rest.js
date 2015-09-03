let urlPrefix = "/api/";

export function send(request) {
	return new Promise(function (resolve, reject) {
		var xhr = new XMLHttpRequest;
		xhr.addEventListener("error", reject);
		xhr.addEventListener("load", (result) => {
			let responseJson = JSON.parse(xhr.responseText);
			resolve(responseJson);
		} );
		xhr.open(request.method || "GET", urlPrefix + request.url);
		xhr.setRequestHeader("Accept", "application/json");
		xhr.send(null);
	});
}


