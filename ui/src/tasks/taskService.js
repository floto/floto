import Promise from "bluebird";

import websocketService from "../util/websocketService.js";
import notificationService from "../util/notificationService.js";
import {send} from "../util/rest.js";
import * as actions from "../actions/actions.js";


var taskService = {};

var taskCompletionPromises = {};

function makeDeferred() {
	let result = {};
	result.promise = new Promise((resolve, reject) => {
		result.resolve = resolve;
		result.reject = reject;
	});
	return result;
}


taskService.getTaskCompletionPromise = function getTaskCompletionPromise(taskId) {
	var deferred = taskCompletionPromises[taskId] = taskCompletionPromises[taskId] || makeDeferred();
	return deferred.promise;
};

let globalDispatch;

taskService.httpPost = function httpPost(dispatch, url, request) {
	globalDispatch = dispatch;
	return send({url, request, method: "POST"}).then(function (taskInfo) {
		var taskId = taskInfo.taskId;
		var linkText = ' <a href="#/tasks/' + taskId + '">(#' + taskId + ')</a>';
		actions.loadTasks(dispatch);
		notificationService.notify({
			title: "Task started: " + taskInfo.title + linkText,
			type: 'info'
		});
		return taskService.getTaskCompletionPromise(taskId);
	});
};

websocketService.addMessageHandler("taskComplete", function(message) {
	actions.loadTasks(globalDispatch);
	var taskId = message.taskId;
	var deferred = taskCompletionPromises[taskId];
	var linkText = ' <a onclick="$(this).closest(\'.ui-pnotify\').find(\'.ui-pnotify-closer\').trigger(\'click\');" href="#/tasks/' + taskId + '">(#' + taskId + ')</a>';
	if (message.status === "success") {
		notificationService.notify({
			title: "Success: " + message.taskTitle + linkText,
			type: 'success'
		});
		deferred.resolve(null);
	} else {
		notificationService.notify({
			title: "Error: " + message.taskTitle + linkText,
			text: message.errorMessage,
			type: 'error',
			hide: false
		});
		deferred.reject(message.errorMessage);
	}
});

export default taskService;


