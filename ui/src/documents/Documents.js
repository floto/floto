import { connect } from 'react-redux';

import { Navigation } from 'react-router';


import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup, Panel} from "react-bootstrap";

export default connect(state => {
	return {
		manifestError: state.manifestError,
		manifest: state.manifest
	};
})(React.createClass({
			displayName: "Documents",
			contextTypes: {
				actions: React.PropTypes.object.isRequired,
				router: React.PropTypes.object.isRequired
			},

		navigateToDocument(document) {
			let newUrl = '/documents/' + document.id;
			this.context.router.push({pathname: newUrl, query: this.props.location.query});
		},

		renderDocument(document) {
			return <tr key={document.title} onClick={this.navigateToDocument.bind(this, document)}>
				<td style={{width: "100%"}}>{document.title}</td>
			</tr>;
		},

			render() {
				let actions = this.context.actions;
				let documents = this.props.manifest.documents || [];
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: "1 1 auto", width: "20%", height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Documents <span className="text-muted">({documents.length})</span></h2>
							</div>
							<div style={{flex: "1 1 auto", overflowY: "scroll"}}>
								<Table bordered striped hover condensed style={{cursor: "pointer"}}>
									<tbody>
									{documents.map(this.renderDocument)}
									</tbody>
								</Table>
							</div>
						</div>
						<div style={{flex: "1 1 auto", width: "80%", paddingLeft: 20, paddingTop: 30, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);




