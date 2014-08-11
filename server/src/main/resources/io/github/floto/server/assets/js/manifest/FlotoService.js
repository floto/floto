(function () {
    "use strict";
    app.factory('FlotoService', function ($resource, $http, $rootScope) {
        var FlotoService = {};

        FlotoService.getManifest = function getManifest() {
            return this.manifest;
        };

        FlotoService.refreshManifest = function refreshManifest() {
            this.manifest = $resource(app.urlPrefix + 'manifest').get();
            this.manifest.$promise.then(function (manifest) {
                if (manifest.site) {
                    $rootScope.domainName = manifest.site.domainName;
                    $rootScope.titleSuffix = " - " + manifest.site.domainName;

                } else {
                    $rootScope.domainName = null;
                    $rootScope.titleSuffix = null;
                }
            });
        };

        FlotoService.compileManifest = function compileManifest() {
            var recompilePromise = $http.post(app.urlPrefix + 'manifest/compile');
            recompilePromise.finally(function () {
                FlotoService.refreshManifest();
            });
            return  recompilePromise;
        };

        FlotoService.getContainerStates = function getContainerStates() {
            return $resource(app.urlPrefix + 'containers/_state').get();
        };

        FlotoService.redeployContainers = function redeployContainers(request) {
            return $http.post(app.urlPrefix + 'containers/_redeploy', request);
        };

        FlotoService.startContainers = function startContainers(request) {
            return $http.post(app.urlPrefix + 'containers/_start', request);
        };

        FlotoService.stopContainers = function stopContainers(request) {
            return $http.post(app.urlPrefix + 'containers/_stop', request);
        };

        FlotoService.purgeContainerData = function purgeContainerData(request) {
            return $http.post(app.urlPrefix + 'containers/_purgeData', request);
        };

        FlotoService.getHostStates = function getHostStates() {
            return $resource(app.urlPrefix + 'hosts/_state').get();
        };

        FlotoService.redeployHosts = function redeployHosts(request) {
            return $http.post(app.urlPrefix + 'hosts/_redeploy', request);
        };

        FlotoService.startHosts = function startHosts(request) {
            return $http.post(app.urlPrefix + 'hosts/_start', request);
        };

        FlotoService.stopHosts = function stopHosts(request) {
            return $http.post(app.urlPrefix + 'hosts/_stop', request);
        };

        FlotoService.deleteHosts = function deleteHosts(request) {
            return $http.post(app.urlPrefix + 'hosts/_delete', request);
        };


        FlotoService.getFilePreview = function getFilePreview(url) {
            var result = {
                content: "Loading..."
            };
            $http.get(app.urlPrefix + url, {transformResponse: function(x) {return x;}}).then(function (response) {
                result.content = response.data;
            });
            return  result;

        };

        FlotoService.getContainerTemplates = function getContainerTemplates(containerName) {

            return this.getManifest().$promise.then(function (manifest) {
                function findImage(imageName) {
                    var image = _.find(manifest.images, {name: imageName});
                    if (!image) {
                        throw "Image " + imageName + " not found";
                    }
                    return image;
                }

                function findContainer(containerName) {
                    var container = _.find(manifest.containers, {name: containerName});
                    if (!container) {
                        throw "Container " + containerName + " not found";
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
        FlotoService.refreshManifest();

        return FlotoService;
    });

    function getShortFileName(filename) {
        return filename.replace(/^.*(\\|\/|\:)/, '');
    }
})();