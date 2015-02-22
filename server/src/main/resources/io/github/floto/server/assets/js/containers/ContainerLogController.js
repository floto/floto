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
		var scrollElement = $pre[0].parentNode;
		var myStreamId = streamId++;
		handlers[myStreamId] = function (data) {
			data.messages.forEach(function (message) {
				var className = "log-" + message.stream;
				$pre.append("<div class='" + className + "'>" + message.log + "</div>");
			});
			if ($scope.autoScroll) {
				scrollElement.scrollTop = scrollElement.scrollHeight;
			}
		};
		WebSocketService.sendMessage({
			type: "subscribeToContainerLog",
			streamId: myStreamId,
			containerName: containerName
		});



	});
})();