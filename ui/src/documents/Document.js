import { connect } from 'react-redux';
import React from 'react';
import PropTypes from 'prop-types';

class Document extends React.Component {
	render() {
		var selectedDocument = this.props.selectedDocument;
		return <div style={{height: "100%", width: "100%"}}>
			<iframe style={{height: "100%", width: "100%", border: "2px solid #CCC"}} src={"/api/documents/" + selectedDocument} />
		</div>;

	}
}

Document.contextTypes = {
	actions: PropTypes.object.isRequired,
	router: PropTypes.object.isRequired
}

export default connect(state => {
	return {
		selectedDocument: state.selectedDocument
	};
})(Document);




