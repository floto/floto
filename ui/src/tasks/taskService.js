import Promise from "bluebird";

import websocketService from "../util/websocketService.js";
import notificationService from "../util/notificationService.js";
import {send} from "../util/rest.js";


var taskService = {};

var taskCompletionPromises = {};

let globalActions = null;

taskService.setActions = function (actions) {
	globalActions = this.actions = actions;
};

function makeDeferred() {
	let result = {};
	result.promise = new Promise((resolve, reject) => {
		result.resolve = resolve;
		result.reject = reject;
	});
	return result;
}


taskService.getTaskCompletionDeferred = function getTaskCompletionPromise(taskId) {
	var deferred = taskCompletionPromises[taskId] = taskCompletionPromises[taskId] || makeDeferred();
	return deferred;
};

let globalStore;

taskService.httpPost = function httpPost(store, url, request, options = {}) {
	globalStore = store;
	return send({url, request, method: "POST", options}).then((taskInfo) => {
		var taskId = taskInfo.taskId;
		var deferred = taskService.getTaskCompletionDeferred(taskId);
		var linkText = ' <a href="#/tasks/' + taskId + '">(#' + taskId + ')</a>';
		notificationService.notify({
			title: "Task started: " + taskInfo.title + linkText,
			type: 'info'
		});
		this.actions.loadTasks(store);
        return deferred.promise;
	});
};

websocketService.addMessageHandler("taskComplete", function (message) {
	globalActions.loadTasks(globalStore);
	var taskId = message.taskId;
	var deferred = taskService.getTaskCompletionDeferred(taskId);
	var linkText = ' <a onclick="$(this).closest(\'.ui-pnotify\').find(\'.ui-pnotify-closer\').trigger(\'click\');" href="#/tasks/' + taskId + '">(#' + taskId + ')</a>';
	if (message.status === "success") {
		if(message.numberOfWarnings) {
			notificationService.notify({
				title: "Task success (with " + message.numberOfWarnings + " warnings): " + message.taskTitle + linkText,
				text: '<a onclick="$(this).closest(\'.ui-pnotify\').find(\'.ui-pnotify-closer\').trigger(\'click\');" href="#/tasks/' + taskId + '">Click here to see details</a>',
				type: 'notice',
				hide: false
			});
		} else {
			notificationService.notify({
				title: "Task success: " + message.taskTitle + linkText,
				type: 'success'
			});
		}
		if(deferred && deferred.resolve) {
			deferred.resolve(null);
		}
	} else {
		notificationService.notify({
			title: "Task error: " + message.taskTitle + linkText,
			text: '<a onclick="$(this).closest(\'.ui-pnotify\').find(\'.ui-pnotify-closer\').trigger(\'click\');" href="#/tasks/' + taskId + '">Click here to see details</a><br>' + message.errorMessage,
			type: 'error',
			hide: false
		});
		var error = new Error(message.errorMessage);
		error.suppressLog = true;
		if(deferred && deferred.reject) {
			deferred.reject(error);
		}
	}
});

var logSubscriptions = {};

function sendMessage(message) {
	websocketService.sendMessage(message);
}
websocketService.addMessageHandler("taskLogEntry", function (message) {
	var streamId = message.streamId;
	logSubscriptions[streamId](message.entry);
});


var nextStreamId = 1;
function getNextStreamId() {
	var streamId = nextStreamId;
	nextStreamId++;
	return streamId;
}
taskService.subscribeToLog = function subscribeToLog(taskId, callback) {
	var streamId = getNextStreamId();
	logSubscriptions[streamId] = callback;
	var message = {
		type: "subscribeToTaskLog",
		taskId: taskId,
		streamId: streamId
	};
	sendMessage(message);
	return streamId;
};

export default taskService;


