export default (state = {}, action = undefined) => {
	if(action.type === "MANIFEST_UPDATED") {
		return Object.assign({}, state, {manifest: action.payload});
	}
	return state;
}
