package io.github.floto.dsl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.floto.dsl.model.*;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class FlotoDslTest {

	FlotoDsl flotoDsl = new FlotoDsl();

	@Test
	public void testSimpleImage() {
		Manifest manifest = generateManifest("simple-image.js", "testing");
		assertEquals(1, manifest.images.size());

		Image image = manifest.images.get(0);
		assertEquals("foobar", image.name);

		ObjectNode buildStep = JsonNodeFactory.instance.objectNode();
		buildStep.put("line", "ubuntu");
		buildStep.put("type", "FROM");
		assertEquals(Arrays.asList(buildStep), image.buildSteps);

	}

	@Test
	public void testSimpleContainer() {
		Manifest manifest = generateManifest("simple-container.js", "testing");
		assertEquals(1, manifest.containers.size());

		Container container = manifest.containers.get(0);
		assertEquals("fizzbuzz", container.name);
		assertEquals("foobar", container.image);
		assertEquals("host1", container.host);
		ObjectNode expectedConfig = JsonNodeFactory.instance.objectNode();
		expectedConfig.put("foo", "bar");
		assertEquals(expectedConfig, container.config);
	}

	@Test
	public void testSimpleHost() {
		Manifest manifest = generateManifest("simple-host.js", "testing");
		assertEquals(1, manifest.hosts.size());

		Host host = manifest.hosts.get(0);
		assertEquals("host1", host.name);
		assertEquals("1.2.3.4", host.ip);
	}

	@Test
	public void testInclude() {
		Manifest manifest = generateManifest("include-test.js", "testing");
		assertEquals(1, manifest.images.size());
	}

	@Test
	public void testEnvironment() {
		Manifest manifest = generateManifest("environment.js", "foobar");
		assertEquals("foobar", manifest.site.path("env").asText());
	}

	@Test
	public void testDocument() {
		Manifest manifest = generateManifest("document.js", "testing");
		assertEquals(1, manifest.documents.size());
		DocumentDefinition documentDefinition = manifest.documents.get(0);
		assertEquals("Don't panic!", documentDefinition.title);
		assertEquals("dont_panic.html", documentDefinition.template);
	}


	private Manifest generateManifest(String name, String environment) {
		return flotoDsl.generateManifest(new File("src/test/resources/js/" + name), environment);
	}

}
