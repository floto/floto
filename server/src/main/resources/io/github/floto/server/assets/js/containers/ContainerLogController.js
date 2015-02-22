(function () {
	"use strict";

	var streamId = 0;

	var handlers = {};

	app.run(function (WebSocketService) {
		WebSocketService.addMessageHandler("containerLogMessages", function (message) {
			var handler = handlers[message.streamId];
			if (handler) {
				handler(message);
			}
		});
	});

	app.controller("ContainerLogController", function ($scope, $stateParams, $element, WebSocketService) {
		$scope.autoScroll = true;
		var containerName = $stateParams["containerName"];
		$scope.containerName = containerName;
		var $pre = $element.find("pre");
		var myStreamId = streamId++;
		handlers[myStreamId] = function (data) {
			data.messages.forEach(function (message) {
				var className = "log-" + message.stream;
				var timestamp = message.time.substr(0, 10) + " " + message.time.substr(11, 8);
				$pre.append("<div class='" + className + "'>" + timestamp + "Z "+ message.log + "</div>");
			});
			$scope.scrollDown();
		};
		WebSocketService.sendMessage({
			type: "subscribeToContainerLog",
			streamId: myStreamId,
			containerName: containerName
		});

		$scope.$on("$destroy", function() {
			WebSocketService.sendMessage({
				type: "unsubscribeFromContainerLog",
				streamId: myStreamId
			});
		});



	});
})();