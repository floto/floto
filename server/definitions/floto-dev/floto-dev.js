
setDomain("virtualbox.site")

site.projectRevision = "1.2.3-foo";


host("localhost", {});

image("elasticsearch", {
    build: function() {
        addTemplate(__DIR__ + "templates/foobar.txt", "/foo");
    },
    configure: function(config) {
        config.webUrl = "http://www.google.com";
        config.version = "1.2.3";
    }
});
image("logstash", {});

container("elasticsearch", {
    image: "elasticsearch",
    host: "localhost"
});

container("logstash", {
    image: "logstash",
    host: "localhost"
});