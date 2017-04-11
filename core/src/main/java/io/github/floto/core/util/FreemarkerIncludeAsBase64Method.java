package io.github.floto.core.util;

import freemarker.core.Environment;
import freemarker.template.*;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.WriterOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

public class FreemarkerIncludeAsBase64Method implements TemplateDirectiveModel {
	private Path rootPath;

	public FreemarkerIncludeAsBase64Method(Path rootPath) {
		this.rootPath = rootPath;
	}

	@Override
	public void execute(Environment environment, Map map, TemplateModel[] templateModels, TemplateDirectiveBody templateDirectiveBody) throws TemplateException, IOException {
		String path = map.get("path").toString();
		Template template = environment.getCurrentTemplate();
		Path templatePath = rootPath.resolve(template.getName());
		File file = templatePath.getParent().resolve(path).toFile();
		OutputStream base64OutputStream = new Base64OutputStream(new CloseShieldOutputStream(new WriterOutputStream(environment.getOut(), "UTF-8")));
		FileUtils.copyFile(file, base64OutputStream);
		base64OutputStream.close();
	}
}
