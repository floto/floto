import { connect } from 'react-redux';

export default connect(state => {
	return {selectedFile: state.selectedFile, selectedFileError: state.selectedFileError};
})(React.createClass({

	componentDidMount() {
		this.scrollDown();
	},

	componentDidUpdate(prevProps) {
		this.scrollDown();
	},

	scrollDown() {
		if(this.scrolledDown || !this.needsScroll) {
			return;
		}
		let domNode = React.findDOMNode(this);
		if(!domNode) {
			return;
		}
		domNode.scrollTop = domNode.scrollHeight;
		this.scrolledDown = true;
	},


	render() {
		var selectedFile = this.props.selectedFile;
		var selectedFileError = this.props.selectedFileError;
		if (!selectedFile) {
			if (selectedFileError) {
				return <div className="alert alert-danger">{selectedFileError.error.message || selectedFileError}</div>;
			}
			return null;
		}
		if(selectedFile.fileName === "buildlog" || selectedFile.fileName === "log") {
			this.needsScroll = true;
		}
		return <div style={{minWidth: "100%", height: "100%", overflow: "scroll"}}>
			<pre
				style={{display: "inline-block", wordBreak: "normal", wordWrap: "normal", overflow: "visible", minWidth: "100%", minHeight: "100%"}}>{selectedFile.content}</pre>
		</div>;
	}
}));

