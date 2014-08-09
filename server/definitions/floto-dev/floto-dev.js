
host("localhost", {});

image("elasticsearch", {
    build: function() {
        addTemplate(__DIR__ + "templates/foobar.txt", "/foo");
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