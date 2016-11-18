package io.github.floto.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TemplateUtil {
	private File rootPath;

	public TemplateUtil(File rootPath) {
		this.rootPath = rootPath;
	}

	public String getTemplate(JsonNode step, Map<String, Object> globalConfig) {
        try {
            String template = step.path("template").asText();
            File templateFile = new File(template);
            if (!templateFile.exists()) {
                throw new IllegalStateException("Template file not found: " + templateFile.getAbsolutePath());
            }
            Configuration cfg = new Configuration();
			cfg.setLogTemplateExceptions(false);
            cfg.setNumberFormat("0.######");
            cfg.setObjectWrapper(new DefaultObjectWrapper());
			cfg.setDirectoryForTemplateLoading(rootPath);
            cfg.setDefaultEncoding("UTF-8");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> localConfig = mapper.reader(Map.class).readValue(step.path("config"));
            Map<String, Object> config = new HashMap<String, Object>(globalConfig);
            config.putAll(localConfig);
            config.put("jsonify", new FreemarkerJsonifyMethod());
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
			Path relativePath = rootPath.toPath().relativize(templateFile.toPath());
			Template templateFunc = cfg.getTemplate(relativePath.toString().replaceAll("\\\\", "/"));
            StringWriter stringWriter = new StringWriter();
			templateFunc.process(config, stringWriter);
            String templated = stringWriter.toString();
            return templated;
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

}
