
site = {};

manifest = {
	images: [],
	containers: [],
	hosts: [],
	site: site,
	files: {}
};

function image(name, definition) {
	_.defaults(definition, {
		prepare: function() {},
		build: function() {},
		configure: function() {}
	});
	var image = {
        name: name,
        definition: definition
    };
	manifest.images.push(image);
}

run = function run(what) {
	currentSteps.push({
                       	    type: "RUN",
                       	    line: what
                       	});
};

env = function env(key, value) {
	currentSteps.push({
                       	    type: "ENV",
                       	    line: key + " " + value
                       	});
};

from = function from(what) {
	currentSteps.push({
                       	    type: "FROM",
                       	    line: what
                       	});
};

expose = function expose(what) {
	currentSteps.push({
                       	    type: "EXPOSE",
                       	    line: what
                       	});
};

workdir = function workdir(what) {
	currentSteps.push({
                       	    type: "WORKDIR",
                       	    line: what
                       	});
};

cmd = function cmd(what) {
	currentSteps.push({
                       	    type: "CMD",
                       	    line: "while :; do " + what + " ; echo Process exited with status $?; sleep 3; done"
                       	});
};

volume = function volume(path, name) {
	currentSteps.push({
                       	    type: "VOLUME",
                       	    path: path,
                       	    name: name
                       	});
};

addTemplate = function addTemplate(templateName, destination, config) {
	if(!config) {
		config = {};
	}
	currentSteps.push({
                       	    type: "ADD_TEMPLATE",
                       	    template: templateName,
                       	    destination: destination,
                       	    config: config
                       	});
};

addMaven = function addMaven(coordinates, destination) {
	currentSteps.push({
                       	    type: "ADD_MAVEN",
                       	    coordinates: coordinates,
                       	    destination: destination
                       	});
};

download = function download(url, destination) {
	currentSteps.push({
                       	    type: "DOWNLOAD",
                       	    url: url,
                       	    destination: destination
                       	});
};

determineIp = function determineIp(command) {
	currentSteps.push({
                       	    type: "DETERMINE_IP",
                       	    command: command
                       	});
};

runAsUser = function runAsUser(command) {
	currentSteps.push({
                       	    type: "RUN_AS_USER",
                       	    command: command
                       	});
};

copyFiles = function copyFiles(fileList, destination) {
	currentSteps.push({
                       	    type: "COPY_FILES",
                       	    fileList: fileList,
                       	    destination: destination
                       	});
};

copyDirectory = function copyFiles(source, destination) {
	currentSteps.push({
                       	    type: "COPY_DIRECTORY",
                       	    source: source,
                       	    destination: destination
                       	});
};


function container(name, definition) {
	definition.name = name;
	manifest.containers.push(definition);
}

function host(name, definition) {
	definition.name = name;
	manifest.hosts.push(definition);
}



function setDomain(domainName) {
	manifest.site.domainName = domainName;
}

var normalize = Java.type('io.github.floto.dsl.util.FilenameUtils').normalize;

function include(filename) {
    var normalizedFile = normalize(filename);
    manifest.files[normalizedFile] = null;
	load(normalizedFile);
}
_currentHostName = null;

function getCurrentHostName() {
    if(!_currentHostName) {
        throw "Current hostname not set";
    }
    return _currentHostName;
}

function getManifest() {
    // Files
	manifest.rootFile = normalize(__ROOT_FILE__);
	manifest.files[manifest.rootFile] = null;

	// Images
	manifest.images.forEach(function (image) {
		currentImage = image;
		currentSteps = [];
		image.buildSteps = currentSteps;
		image.definition.build();
	});

	// Container prepare
	manifest.containers.forEach(function (container) {
		var image = findImage(container.image);
		container.config = container.config || {};
		var config = _.clone(container.config);
		config.host = findHost(container.host);
		if(image.definition.prepare) {
		    _currentHostName = config.host.name;
			image.definition.prepare(config);
			container.config = config;
			_currentHostName = null;
		}
	});

	// Container configure
	manifest.containers.forEach(function (container) {
		var image = findImage(container.image);
		currentSteps = [];
		container.configureSteps = currentSteps;
		container.config = container.config || {};
		var config = _.clone(container.config);
		config.host = findHost(container.host);
        _currentHostName = config.host.name;
		image.definition.configure(config);
		_currentHostName = null;
		delete container.config.host;
	});

	// Host postDeploy
	manifest.hosts.forEach(function (host) {
		currentSteps = [];
		host.postDeploySteps = currentSteps;
		if(host.postDeploy) {
		    host.postDeploy(host);
		}
	});

	// Host reconfigure
	manifest.hosts.forEach(function (host) {
		currentSteps = [];
		host.reconfigureSteps = currentSteps;
		if(host.reconfigure) {
		    host.reconfigure(host, {httpProxy: httpProxy});
		}
	});

	return manifest;
}

function findImage(imageName) {
	var image = _.find(manifest.images, {name: imageName});
	if(!image) {
		throw "Image " + imageName + " not found";
	}
	return image;
}

function findHost(hostName) {
	var host = _.find(manifest.hosts, {name: hostName});
	if(!host) {
		throw "Host " + hostName + " not found";
	}
	return host;
}

var git = {
    describe: function describe(directory) {
        return Java.type('io.github.floto.dsl.util.GitUtils').describe(directory);
    },
    timestamp: function describe(directory) {
        return Java.type('io.github.floto.dsl.util.GitUtils').timestamp(directory);
    }
};

var maven = {
    getVersion: function getVersion(directory) {
        return Java.type('io.github.floto.dsl.util.MavenUtils').getVersion(directory);
    },

    setRepositories: function setRepositories(repositories) {
        site.maven = site.maven || {};
        site.maven.repositories = repositories;
    }
};

var floto = {
    version: Java.type('io.github.floto.util.VersionUtil').version
};
