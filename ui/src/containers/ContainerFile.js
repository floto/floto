import { connect } from 'react-redux';

export default connect(state => {
	return {selectedFile: state.selectedFile, selectedFileError: state.selectedFileError}
})(React.createClass({
	render() {
		var selectedFile = this.props.selectedFile;
		var selectedFileError = this.props.selectedFileError;
        if(!selectedFile) {
			if(selectedFileError) {
				return <div className="alert alert-danger">{selectedFileError.message || selectedFileError}</div>
			}
			return null;
		}
		return <div style={{minWidth: "100%", minHeight: "100%"}}>
			<pre style={{display: "inline-block", wordBreak: "normal", wordWrap: "normal", overflow: "visible", minWidth: "100%", minHeight: "100%"}}>{selectedFile}</pre>
		</div>;
	}
}));





