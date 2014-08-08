package io.github.floto.dsl.model;

public class BuildStep {
	public enum Type {
		FROM, RUN, ADD_TEMPLATE, ADD_MAVEN
	}

	public Type type;
	public String line;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BuildStep buildStep = (BuildStep) o;

		if (line != null ? !line.equals(buildStep.line) : buildStep.line != null) return false;
		if (type != null ? !type.equals(buildStep.type) : buildStep.type != null) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = type != null ? type.hashCode() : 0;
		result = 31 * result + (line != null ? line.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "BuildStep{" +
				"type='" + type + '\'' +
				", line='" + line + '\'' +
				'}';
	}
}
