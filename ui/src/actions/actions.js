export function updateManifest(manifest) {
	return {
		type: "UDPATE_MANIFEST",
		payload: { manifest }
	}
}
