import { connect } from 'react-redux';

import { Navigation } from 'react-router';


import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup, Panel} from "react-bootstrap";

export default connect(state => {
	return {
		selectedDocument: state.selectedDocument
	};
})(React.createClass({
			displayName: "Document",
			contextTypes: {
				actions: React.PropTypes.object.isRequired,
				router: React.PropTypes.object.isRequired
			},

			render() {
				var selectedDocument = this.props.selectedDocument;
				return <div style={{height: "100%", width: "100%"}}>
					<iframe style={{height: "100%", width: "100%", border: "2px solid #CCC"}} src={"/api/documents/" + selectedDocument} />
				</div>;

			}
		}
	)
);




