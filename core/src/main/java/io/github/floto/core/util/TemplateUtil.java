package io.github.floto.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class TemplateUtil {
	private File rootPath;
	
	private Logger log = LoggerFactory.getLogger(TemplateUtil.class);

	public TemplateUtil(File rootPath) {
		this.rootPath = rootPath;
	}

	public String getTemplate(JsonNode step, Map<String, Object> globalConfig) {
		try {
			String template = step.get("template").asText();

			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> localConfig = mapper.reader(Map.class)
					.readValue(step.path("config"));

			return getTemplate(template, localConfig, globalConfig);
		} catch (Throwable e) {
			throw Throwables.propagate(e);
		}
	}

	public String getTemplate(String template, Map<String, Object> localConfig, Map<String, Object> globalConfig) {
		try {
			File templateFile = new File(template);
			if (!templateFile.exists()) {
				throw new IllegalStateException("Template file not found: "
						+ templateFile.getAbsolutePath());
			}
			Configuration cfg = new Configuration();
			cfg.setLogTemplateExceptions(false);
			cfg.setNumberFormat("0.######");
			cfg.setObjectWrapper(new DefaultObjectWrapper());
			cfg.setDirectoryForTemplateLoading(rootPath);
			cfg.setDefaultEncoding("UTF-8");
			Map<String, Object> config = new HashMap<String, Object>(
					globalConfig);
			config.putAll(localConfig);
			config.put("jsonify", new FreemarkerJsonifyMethod());
			config.put("include_as_base64",
					new FreemarkerIncludeAsBase64Method(rootPath.toPath()));

			cfg.setTemplateExceptionHandler(
					TemplateExceptionHandler.DEBUG_HANDLER);
			Path relativePath = rootPath.toPath()
					.relativize(templateFile.toPath());
			Template templateFunc = cfg.getTemplate(
					relativePath.toString().replaceAll("\\\\", "/"));
			StringWriter stringWriter = new StringWriter();
			templateFunc.process(config, stringWriter);
			String templated = stringWriter.toString();
			return templated;
		} catch (Throwable e) {
			throw Throwables.propagate(e);
		}
	}

}
