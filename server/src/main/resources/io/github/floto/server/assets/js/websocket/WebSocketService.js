(function () {
	"use strict";
	app.factory('WebSocketService', function ($q) {
		var WebSocketService = {};

		var messageHandlers = {};
		WebSocketService.addMessageHandler = function addMessageHandler(messageType, messageHandlerFn) {
			messageHandlers[messageType] = messageHandlerFn;
		};

		WebSocketService.sendMessage = function sendMessage(message) {
			 wsPromise.then(function (ws) {
				 ws.send(JSON.stringify(message));
			 });
		};

		var wsPromise;

		var loc = window.location;
		var websocketUri;
		if (loc.protocol === "https:") {
			websocketUri = "wss:";
		} else {
			websocketUri = "ws:";
		}
		websocketUri += "//" + loc.host;
		websocketUri += loc.pathname + "api/_websocket";
		function connectWebSocket(timeout) {
			var deferred = $q.defer();
			wsPromise = deferred.promise;
			setTimeout(function () {
				var ws = new WebSocket(websocketUri);

				ws.onopen = function () {
					console.log("Connected")
					deferred.resolve(ws);
				};
				ws.onmessage = function (evt) {
					var message = JSON.parse(evt.data);
					var messageHandlerFn = messageHandlers[message.type];
					if (messageHandlerFn) {
						messageHandlerFn(message);
					} else {
						console.log("ERROR: unknown websocket message " + message.type);
						console.log(message);
					}
				};
				ws.onclose = function () {
					// websocket is closed.
					console.log("Connection is closed...");
					connectWebSocket(1000);
				};
			}, timeout);
		}

		connectWebSocket(0);

		return WebSocketService;
	});

})();