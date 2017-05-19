import { connect } from 'react-redux';
import Icon from 'react-fa';
import {Table} from "react-bootstrap";
import React from 'react';
import PropTypes from 'prop-types';

class Documents extends React.Component {

	constructor() {
		super();

		this.navigateToDocument = this.navigateToDocument.bind(this);
		this.renderDocument = this.renderDocument.bind(this);
	}

	navigateToDocument(document) {
		let newUrl = '/documents/' + document.id;
		this.context.router.push({pathname: newUrl, query: this.props.location.query});
	}

	renderDocument(document) {
		return <tr key={document.title} onClick={this.navigateToDocument.bind(this, document)}>
			<td style={{width: "100%"}}>{document.title}<span className="pull-right"> <a href={"/api/documents/"+document.id} download={document.title+".html"}><Icon name="download" /> Download</a></span></td>
		</tr>;
	}

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

Documents.contextTypes = {
	actions: PropTypes.object.isRequired,
	router: PropTypes.object.isRequired
}

export default connect(state => {
	return {
		manifestError: state.manifestError,
		manifest: state.manifest
	};
})(Documents);




