
require('expose?jQuery!expose?$!jquery');
require("script!../../lib/pnotify/pnotify.custom.min.js");

PNotify.prototype.options.styling = "bootstrap3";
PNotify.prototype.options.history.menu = true;
var notificationService = {};
var stack_bottomright = {
	"dir1": "up",
	"dir2": "left",
	"firstpos1": 25,
	"firstpos2": 25
};
var defaults = {
	addclass: "stack-bottomright",
	stack: stack_bottomright,
	width: "500px",
	type: 'info'
};
notificationService.notify = function (settings) {
	settings = $.extend({}, defaults, settings);
	new PNotify(settings);
};
export default notificationService;
