## Patches

This sections details how patches work.

Patches are floto's way of distributing complete and self-contained system images.
This allows a simple and robust way to install and update systems that are operating in a insular network.

Incremental patches are used to update existing systems and only provide the delta to the previous reference patch state, i.e. the changed image layers.
This means that these incremental patches are relatively small.

Initial installation can also be done via so called initial patches.
These work basically the same as incremental patches, but the "reference" patch state is a completely empty system.
That means that this initial patch contains all the required image layers.

### Patch file format

Patch file are ZIP files that contain the following files:

 * a file `VERSION.txt` that contains the string `floto patch v1`
 * a file `patch-description.json` that describes the patch itself, document below
 * a directory `conf` that contains a copy of the floto root directory
 * a directory `images` that contains all contained docker image layers

#### Patch description

The `patch-description.json` describes a patch and contains the following fields:

* **creationDate**: The date the patch was create in ISO8601 format, UTC timezone, e.g. `2015-11-18T11:38:05.717Z`
* **id**: The id of this patch looking something like this: `2015-11-18T11-38-05.717Z-metascope-deployment-2.1.6-16-g31cdcfc-dirty.flotodev`
* **revision**: The git revision of this patch
* **name**: The name of this patch
* **comment**: The patch comment, intended to give additional information to human operators
* **siteName**: The name of the site that this patch was created for
* **rootDefinitionFile**: The root definition file to create the configuration manifest
* **parentId***: The id of the parent patch
* **parentRevision***: The git revision of the parent patch
* **requiredImageIds**: An array containing the IDs of all required images
* **containedImageIds**: An array containing the IDS of all images contained in this patch file
* **imageMap**: an object mapping image names to image IDs

\* Not applicable for initial patches

#### Patch creation

Patches are created on special patch-maker VMs in the following manner:

 1. For delta patches the parent patch images are imported to benefit from caching etc.
 1. All docker base images are built
 1. All new image layers are downloaded
 1. The new patch file is created from the new image layers

#### Patch installation

Patches are uploaded, but need to be "activated" to actually use them. This simplifies version rollbacks. After activation, the changed containers need to be redeployed.
