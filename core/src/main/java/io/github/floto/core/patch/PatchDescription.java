package io.github.floto.core.patch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatchDescription {

    // Date that this patch was created on
    public Instant creationDate;

    // The site name (site.projectName)
    public String siteName;

    // The revision string of this patch
    public String revision;

    // The revision string that this patch is based on (null in case of an initial patch)
    public String parentRevision;

    // The list of required docker image ids
    public List<String> requiredImageIds = new ArrayList<>();

    // The list of required docker images
    public List<String> containedImages = new ArrayList<>();

    // Map of base image names to to image ids
    public Map<String, String> imageMap = new HashMap<>();

}
