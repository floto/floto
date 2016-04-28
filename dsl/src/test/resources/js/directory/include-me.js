image("foobar", function() {
});

host("bar", {
	ip: "1.2.3.4"
});

container("foo", {
	image: "foobar",
	host: "bar"
});
