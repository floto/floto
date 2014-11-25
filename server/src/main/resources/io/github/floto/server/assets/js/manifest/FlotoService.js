(function () {
    "use strict";
    app.factory('FlotoService', function ($resource, $http, $rootScope, TaskService) {
        var FlotoService = {};

        FlotoService.getManifest = function getManifest() {
            return this.manifest;
        };

        FlotoService.refreshManifest = function refreshManifest() {
            this.manifest = $resource(app.urlPrefix + 'manifest').get();
            this.manifest.$promise.then(function (manifest) {
                if (manifest.site) {
                    $rootScope.domainName = manifest.site.domainName;
                    $rootScope.site = manifest.site;
                    $rootScope.titleSuffix = " - " + (manifest.site.projectName || manifest.site.domainName);
                    if(manifest.site.environment) {
                        $rootScope.titleSuffix += " (" + manifest.site.environment + ")";
                    }
                } else {
                    $rootScope.domainName = null;
                    $rootScope.titleSuffix = null;
                    $rootScope.site = null;
                }
            });
        };

        FlotoService.compileManifest = function compileManifest() {
            var recompilePromise = TaskService.httpPost(app.urlPrefix + 'manifest/compile');
            recompilePromise.finally(function () {
                FlotoService.refreshManifest();
            });
            return  recompilePromise;
        };

        FlotoService.getContainerStates = function getContainerStates() {
            return $resource(app.urlPrefix + 'containers/_state').get();
        };

        FlotoService.redeployContainers = function redeployContainers(request) {
            return TaskService.httpPost(app.urlPrefix + 'containers/_redeploy', request);
        };

        FlotoService.startContainers = function startContainers(request) {
            return TaskService.httpPost(app.urlPrefix + 'containers/_start', request);
        };

        FlotoService.stopContainers = function stopContainers(request) {
            return TaskService.httpPost(app.urlPrefix + 'containers/_stop', request);
        };

        FlotoService.purgeContainerData = function purgeContainerData(request) {
            return TaskService.httpPost(app.urlPrefix + 'containers/_purgeData', request);
        };

        FlotoService.getHostStates = function getHostStates() {
            return $resource(app.urlPrefix + 'hosts/_state').get();
        };

        FlotoService.redeployHosts = function redeployHosts(request) {
            return TaskService.httpPost(app.urlPrefix + 'hosts/_redeploy', request);
        };

        FlotoService.startHosts = function startHosts(request) {
            return TaskService.httpPost(app.urlPrefix + 'hosts/_start', request);
        };

        FlotoService.stopHosts = function stopHosts(request) {
            return TaskService.httpPost(app.urlPrefix + 'hosts/_stop', request);
        };

        FlotoService.deleteHosts = function deleteHosts(request) {
            return TaskService.httpPost(app.urlPrefix + 'hosts/_delete', request);
        };

        FlotoService.exportHosts = function exportHosts(request) {
            return TaskService.httpPost(app.urlPrefix + 'hosts/_export', request);
        };

        FlotoService.getFilePreview = function getFilePreview(url) {
            var result = {
                loading: true
            };
            $http.get(app.urlPrefix + url, {suppressErrorNotfications: true, transformResponse: function(x) {return x;}}).then(function (response) {
                result.loading = false;
                result.content = response.data;
            }).catch(function (error) {
	            var data = JSON.parse(error.data);
                result.loading = false;
                result.error = data.message;
            });
            return  result;

        };

        FlotoService.getContainerTemplates = function getContainerTemplates(containerName) {

            return this.getManifest().$promise.then(function (manifest) {
                function findImage(imageName) {
                    var image = _.find(manifest.images, {name: imageName});
                    if (!image) {
                        image = {buildSteps:[]};
                    }
                    return image;
                }

                function findContainer(containerName) {
                    var container = _.find(manifest.containers, {name: containerName});
                    if (!container) {
	                    container = {name: containerName, configureSteps: []};
                    }
                    return container;
                }

                var container = findContainer(containerName);
                var image = findImage(container.image);
                var templates = [];
                var steps = image.buildSteps.concat(container.configureSteps);
                steps.forEach(function (buildStep) {
                    if (buildStep.type === "ADD_TEMPLATE") {
                        templates.push({
                            name: getShortFileName(buildStep.destination),
                            destination: buildStep.destination
                        });
                    }
                });

                return templates;
            });

        };

        FlotoService.getHostTemplates = function getHostTemplates(hostName) {

            return this.getManifest().$promise.then(function (manifest) {
                function findHost(hostName) {
                    var host = _.find(manifest.hosts, {name: hostName});
                    if (!host) {
                        throw "Host " + hostName + " not found";
                    }
                    return host;
                }

                var host = findHost(hostName);
                var templates = [];
                var steps = host.postDeploySteps;
                steps.forEach(function (buildStep) {
                    if (buildStep.type === "ADD_TEMPLATE") {
                        templates.push({
                            name: getShortFileName(buildStep.destination),
                            destination: buildStep.destination
                        });
                    }
                });

                return templates;
            });

        };

	    FlotoService.destroyUnmanagedContainer = function destroyUnmanagedContainer(request) {
		    return TaskService.httpPost(app.urlPrefix + 'containers/_destroyUnmanaged', request);
	    };
        FlotoService.refreshManifest();

        return FlotoService;
    });

    function getShortFileName(filename) {
        return filename.replace(/^.*(\\|\/|\:)/, '');
    }
})();