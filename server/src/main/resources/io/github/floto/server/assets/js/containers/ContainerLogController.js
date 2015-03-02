(function () {
	"use strict";

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
		$scope.showTimestamps = true;
		var containerName = $stateParams["containerName"];
		$scope.containerName = containerName;
		var timestampElement = $element.find(".log-timestamps");
		var messageElement = $element.find(".log-messages");
		var myStreamId = +(new Date()) + "-" + Math.random();
		handlers[myStreamId] = function (data) {
			data.messages.forEach(function (message) {
				var className = "log-" + message.stream;
				var log = _.escape(message.log);
				var timestamp = message.time.substr(0, 10) + " " + message.time.substr(11, 8);
				timestampElement.append("<div class='" + className + "'>" +timestamp + "Z</div>");
				messageElement.append("<div class='" + className + "'>" + log + "</div>");
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