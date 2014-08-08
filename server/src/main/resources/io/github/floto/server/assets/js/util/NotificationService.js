(function () {
	"use strict";
	app.factory('NotificationService', function () {
		$.pnotify.defaults.styling = "bootstrap3";
		var notificationService = {};
		var stack_bottomright = {
			"dir1": "up",
			"dir2": "left",
			"firstpos1": 25,
			"firstpos2": 25
		};
		var defaults = {
			addclass: "stack-bottomright",
			stack: stack_bottomright
		};
		notificationService.notify = function (settings) {
			settings = $.extend({}, defaults, settings);
			$.pnotify(settings);
		};
		return notificationService;
	});
})();