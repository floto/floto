import { connect } from 'react-redux';
import React from 'react';

class Document extends React.Component {
	render() {
		var selectedDocument = this.props.selectedDocument;
		return <div style={{height: "100%", width: "100%"}}>
			<iframe style={{height: "100%", width: "100%", border: "2px solid #CCC"}} src={"/api/documents/" + selectedDocument} />
		</div>;

	}
}

Document.contextTypes = {
	actions: React.PropTypes.object.isRequired,
	router: React.PropTypes.object.isRequired
}

export default connect(state => {
	return {
		selectedDocument: state.selectedDocument
	};
})(Document);




