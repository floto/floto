import { connect } from 'react-redux';

export default connect(state => {
	return {selectedFile: state.selectedFile}
})(React.createClass({
	render() {
		var selectedFile = this.props.selectedFile;
        if(!selectedFile) {
			return null;
		}
		return <div style={{minWidth: "100%", minHeight: "100%"}}>
			<pre style={{display: "inline-block", wordBreak: "normal", wordWrap: "normal", overflow: "visible", minWidth: "100%", minHeight: "100%"}}>{selectedFile}</pre>
		</div>;
	}
}));





