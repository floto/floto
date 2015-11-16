package io.github.floto.core.patch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PatchCreationParams {
	public String parentPatchId;
	public String name;
	public String comment;
}
