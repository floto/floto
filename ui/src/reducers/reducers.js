export default (state = {}, action = undefined) => {
	if(action.type === "UPDATE_MANIFEST") {
		return Object.assign({}, state, {manifest: action.payload});
	}
	return state;
}
