package io.github.floto.dsl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.google.common.base.Throwables;
import io.github.floto.dsl.model.Manifest;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FlotoDsl {
	private static final boolean IGNORE_UNKNOWN_PROPERTIES = false;
	Logger log = LoggerFactory.getLogger(FlotoDsl.class);

    private Map<String, Object> globals = new HashMap<>();
	private final ObjectMapper objectMapper;

	{
		objectMapper = new ObjectMapper();
		if (IGNORE_UNKNOWN_PROPERTIES) {
			objectMapper.addHandler(new DeserializationProblemHandler() {
				@Override
				public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser jp, JsonDeserializer<?> deserializer, Object beanOrClass, String propertyName) throws IOException, JsonProcessingException {
					Class<? extends Object> ref;
					if (beanOrClass instanceof Class<?>) {
						ref = (Class<?>) beanOrClass;
					} else {
						ref = beanOrClass.getClass();
					}
					log.warn("Cannot map property {} - {} at {}", propertyName, ref.toString(), jp.getCurrentLocation());
					jp.skipChildren();
					return true;
				}
			});
		}

	}

	public Manifest generateManifest(File file, String environment) {
		return toManifest(generateManifestString(file, environment));
	}

	private void evalLibrary(ScriptEngine engine, String library) throws IOException, ScriptException {
		engine.getContext().setAttribute(ScriptEngine.FILENAME, "./" + library, ScriptContext.ENGINE_SCOPE);
		String flotoDslScript = IOUtils.toString(FlotoDsl.class.getResource("/js/" + library));
		engine.eval(flotoDslScript);
	}

	public JsonNode generateManifestJson(File file, String environment) {
		try {
			String manifestString = generateManifestString(file, environment);
			// use the ObjectMapper to read the json string and create a tree
			JsonNode manifest = objectMapper.readTree(manifestString);
			return manifest;
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}
	}

	public String generateManifestString(File file, String environment) {
		ScriptEngineManager engineManager =
				new ScriptEngineManager();
		ScriptEngine engine =
				engineManager.getEngineByName("nashorn");
		try {
            engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE).put("__ROOT_FILE__", file.getAbsolutePath());
			engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE).put("ENVIRONMENT", environment);
            globals.forEach((key, value) -> {
                engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE).put(key, value);
            });
			evalLibrary(engine, "lodash.js");
			evalLibrary(engine, "floto-dsl.js");
			engine.getContext().setAttribute(ScriptEngine.FILENAME, file.getPath(), ScriptContext.ENGINE_SCOPE);
			String script = IOUtils.toString(new URL("file:" + file.getPath()));
			engine.eval(script);
			engine.getContext().setAttribute(ScriptEngine.FILENAME, "./get-manifest.js", ScriptContext.ENGINE_SCOPE);
			String manifestString = (String) engine.eval("JSON.stringify(getManifest(),null, '\\t')");
			return manifestString;
		} catch (Throwable e) {
			throw Throwables.propagate(e);
		}
	}

	public Manifest toManifest(String manifestString) {
		try {
			return objectMapper.reader(Manifest.class).readValue(manifestString);
		} catch (Throwable e) {
			throw new RuntimeException("Unable to parse manifest:\n" + manifestString, e);
		}
	}

    public void setGlobal(String key, String value) {
        globals.put(key, value);
    }
}
