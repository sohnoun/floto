package io.github.floto.core.patch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PatchDescription {

    // Date that this patch was created on
    public Instant creationDate;

	// Name of the patch
	public String name;

	// Patch comment
	public String comment;

	// The site name (site.projectName)
    public String siteName;

    // The root definition file
    public String rootDefinitionFile;

    // The site-unique patch id
    public String id;

    // The revision string of this patch
    public String revision;

    // The revision string that this patch is based on (null in case of an initial patch)
    public String parentRevision;

    // The id of the patch that this patch is based on (null in case of an initial patch)
    public String parentId;

	// The name of the patch that this patch is based on (null in case of an initial patch)
	public String parentName;

	// Map of base image names to to image ids
    public Map<String, String> imageMap = new HashMap<>();

	// Map of root image tags to to image ids
	public Map<String, String> rootImageMap = new HashMap<>();

    // The list of required docker image ids
    public List<String> requiredImageIds = new ArrayList<>();

    // The list of required docker images
    public List<String> containedImageIds = new ArrayList<>();

	// The username of the user that created this patch
	public String author;

	// The software used to create this patch
	public String producer;
}
