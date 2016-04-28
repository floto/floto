image("foobar", {
	prepare:  function() {
    },
	build:  function() {
		from("ubuntu");
	},
	configure:  function() {
    }
});

host("bar", {
	ip: "1.2.3.4"
});

container("foo", {
	image: "foobar",
	host: "bar"
});
