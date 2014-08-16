
host("localhost", {});

image("elasticsearch", {
    build: function() {
        addTemplate(__DIR__ + "templates/foobar.txt", "/foo");
    },
    configure: function(config) {
        config.webUrl = "http://www.google.com";
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