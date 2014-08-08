package io.github.floto.core.util;

import com.google.common.base.Throwables;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateHelper {

    private final Configuration configuration;

    public TemplateHelper(Class<?> templateClass, String prefix) {
        this();
        configuration.setClassForTemplateLoading(templateClass, prefix);
    }

    private TemplateHelper() {
        configuration = new Configuration();
        configuration.setNumberFormat("0.######");
        configuration.setObjectWrapper(new DefaultObjectWrapper());
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
    }

    public String template(String templateName, Object templateParameters) {
        Template template;
        try {
            template = configuration.getTemplate(templateName);
            StringWriter stringWriter = new StringWriter();
            template.process(templateParameters, stringWriter);
            String templated = stringWriter.toString();
            return templated;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}
