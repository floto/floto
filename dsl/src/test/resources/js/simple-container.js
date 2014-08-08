image("foobar", {
	prepare:  function() {
    },
	build:  function() {
		from("ubuntu");
	},
	configure:  function() {
    }
});

container("fizzbuzz", {
	image: "foobar",
	host: "host1",
	config: {
		"foo": "bar"
	}
});

host("host1", {
	ip: "1.2.3.4"
});
