(function (global) {
	"use strict";
	global.site = {};
	var site = global.site;

	if(typeof PATCH_INFO !== "undefined" && PATCH_INFO) {
		global.PATCH = JSON.parse(PATCH_INFO);
    }


	global.manifest = {
		images: [],
		containers: [],
		hosts: [],
		site: site,
		files: {},
		documents: []
	};
	var manifest = global.manifest;

	global.image = function image(name, definition) {
		_.defaults(definition, {
			prepare: function () {
			},
			build: function () {
			},
			configure: function () {
			}
		});
		var imageDef = {
			name: name,
			definition: definition
		};
		manifest.images.push(imageDef);
	};
	
	// deprecated
	global.setImageRegistry = function setImageRegistry(imageRegistry) {
		java.lang.System.out.println("WARNING: using deprecated command 'setImageRegistry()' in configuration, a registry is not needed anymore in floto >= 2.0");
	};

	global.run = function run(what) {
		currentSteps.push({
			type: "RUN",
			line: what
		});
	};

	global.env = function env(key, value) {
		currentSteps.push({
			type: "ENV",
			line: key + " " + value
		});
	};

	global.from = function from(what) {
		currentSteps.push({
			type: "FROM",
			line: what
		});
	};

	global.expose = function expose(what) {
		currentSteps.push({
			type: "EXPOSE",
			line: what
		});
	};

	global.workdir = function workdir(what) {
		currentSteps.push({
			type: "WORKDIR",
			line: what
		});
	};
	
	global.user = function user(username) {
		currentSteps.push({
			type: "USER",
			line: username
		});
	};

	global.cmd = function cmd(what) {
		currentSteps.push({
			type: "CMD",
			// Setup restart and signal handling, propagating SIGTERM to all children to allow processes to clean up
			line: '["/bin/bash", "-c", "DONE=false; trap \\"echo $(date --utc +%FT%TZ) FLOTO: Propagating SIGTERM;DONE=true;kill -15 -1\\" SIGTERM; while :; do echo $(date --utc +%FT%TZ) FLOTO: Starting ; ' + what + ' & wait ; echo $(date --utc +%FT%TZ) FLOTO: Process exited with status $?; if $DONE ; then echo $(date --utc +%FT%TZ) FLOTO: Terminating gracefully; wait; break ; fi ; sleep 3; done"]'
		});
	};

	global.volume = function volume(path, name, options) {
		currentSteps.push({
			type: "VOLUME",
			path: path,
			name: name,
			options: options
		});
	};

	global.mount = function volume(hostPath, containerPath) {
		currentSteps.push({
			type: "MOUNT",
			hostPath: hostPath,
			containerPath: containerPath
		});
	};


	global.addTemplate = function addTemplate(templateName, destination, config) {
		if (!config) {
			config = {};
		}
		currentSteps.push({
			type: "ADD_TEMPLATE",
			template: templateName,
			destination: destination,
			config: config
		});
	};
	
	global.addFile = function addFile(file, destination) {
		currentSteps.push({
			type: "ADD_FILE",
			file: file,
			destination: destination,
		});
	};

	global.addMaven = function addMaven(coordinates, destination) {
		currentSteps.push({
			type: "ADD_MAVEN",
			coordinates: coordinates,
			destination: destination
		});
	};

	global.addManifestJson = function addManifestJson(destination) {
		currentSteps.push({
			type: "ADD_MANIFEST_JSON",
			destination: destination
		});
	};

	global.download = function download(url, destination) {
		currentSteps.push({
			type: "DOWNLOAD",
			url: url,
			destination: destination
		});
	};

	global.determineIp = function determineIp(command) {
		currentSteps.push({
			type: "DETERMINE_IP",
			command: command
		});
	};

	global.runAsUser = function runAsUser(command) {
		currentSteps.push({
			type: "RUN_AS_USER",
			command: command
		});
	};

	global.copyFiles = function copyFiles(fileList, destination) {
		currentSteps.push({
			type: "COPY_FILES",
			fileList: fileList,
			destination: destination
		});
	};

	global.copyDirectory = function copyDirectory(source, destination, options) {
		currentSteps.push({
			type: "COPY_DIRECTORY",
			source: source,
			destination: destination,
			options: options
		});
	};

	global.setHostOnlyIpVBoxWin = function setHostOnlyIpVBoxWin(vmname){
		currentSteps.push({
			type: "SET_HOST_ONLY_IP",
			vmname: vmname
		});
		
	};

	global.container = function container(name, definition) {
		definition.name = name;
		manifest.containers.push(definition);
	};

	global.host = function host(name, definition) {
		definition.name = name;
		if (definition.vmConfiguration && !definition.vmConfiguration.vmName) {
			definition.vmConfiguration.vmName = definition.name;
		}
		manifest.hosts.push(definition);
	};

	global.document = function document(definition) {
		if(!definition.id) {
			definition.id = definition.title;
		}
		definition.id = definition.id.replace(/[^a-zA-Z0-9]/g, "_");
		manifest.documents.push(definition);
	};


	global.setDomain = function setDomain(domainName) {
		manifest.site.domainName = domainName;
	};

	var normalize = global.normalize = Java.type('io.github.floto.dsl.util.FilenameUtils').normalize;

	global.include = function include(filename) {
		var normalizedFile = normalize(filename);
		manifest.files[normalizedFile] = null;
		load(normalizedFile);
	};

	var _currentHostName = null;

	global.getCurrentHostName = function getCurrentHostName() {
		if (!_currentHostName) {
			throw "Current hostname not set";
		}
		return _currentHostName;
	};
	var currentSteps;

	global.getManifest = function getManifest() {
		// Files
		manifest.rootFile = normalize(__ROOT_FILE__);
		manifest.files[manifest.rootFile] = null;

		// Images
		manifest.images.forEach(function (image) {
			global.currentImage = image;
			currentSteps = [];
			image.buildSteps = currentSteps;
			image.definition.build();
		});

		// Container prepare
		manifest.containers.forEach(function (container) {
			var image = findImage(container.image);
			container.config = container.config || {};
			var config = container.config;
			config.host = findHost(container.host);
			if (image.definition.prepare) {
				_currentHostName = config.host.name;
				image.definition.prepare(config, container);
				container.config = config;
				_currentHostName = null;
			}
		});

		// Container configure
		manifest.containers.forEach(function (container) {
			var image = findImage(container.image);
			currentSteps = [];
			container.configureSteps = currentSteps;
			var config = container.config;
			config.host = findHost(container.host);
			_currentHostName = config.host.name;
			image.definition.configure(config, container);
			_currentHostName = null;
			delete container.config.host;
		});

		// Host postDeploy
		manifest.hosts.forEach(function (host) {
			currentSteps = [];
			host.postDeploySteps = currentSteps;
			if (host.postDeploy) {
				host.postDeploy(host);
			}
		});

		// Host reconfigure
		manifest.hosts.forEach(function (host) {
			currentSteps = [];
			host.reconfigureSteps = currentSteps;
			if (host.reconfigure) {
				host.reconfigure(host, {httpProxy: global.httpProxy});
			}
		});

		return manifest;
	};

	function findImage(imageName) {
		var image = _.find(manifest.images, {name: imageName});
		if (!image) {
			throw "Image " + imageName + " not found";
		}
		return image;
	}

	function findHost(hostName) {
		var host = _.find(manifest.hosts, {name: hostName});
		if (!host) {
			throw "Host " + hostName + " not found";
		}
		return host;
	}

	global.git = {
		describe: function describe(directory) {
			return Java.type('io.github.floto.dsl.util.GitUtils').describe(directory);
		},
		timestamp: function describe(directory) {
			return Java.type('io.github.floto.dsl.util.GitUtils').timestamp(directory);
		}
	};

	global.maven = {
		getVersion: function getVersion(directory) {
			return Java.type('io.github.floto.dsl.util.MavenUtils').getVersion(directory);
		},

		setRepositories: function setRepositories(repositories) {
			site.maven = site.maven || {};
			site.maven.repositories = repositories;
		}
	};

	global.floto = {
		version: Java.type('io.github.floto.util.VersionUtil').version
	};

	global.warn = function(arg) {
		logger.warn(arg);
	};


})(this);
