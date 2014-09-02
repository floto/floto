package io.github.floto.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.SimpleHash;
import freemarker.template.SimpleSequence;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

import java.util.List;

public class FreemarkerJsonifyMethod implements TemplateMethodModelEx {
	@Override
	public Object exec(List arguments) throws TemplateModelException {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			Object object = arguments.get(0);
			if (object instanceof SimpleHash) {
				object = ((SimpleHash) object).toMap();
			} else if (object instanceof SimpleSequence) {
				object = ((SimpleSequence) object).toList();
			}
			String string = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
			return string;
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Unable to jsonify " + arguments);
		}
	}
}
