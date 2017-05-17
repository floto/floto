import { Button } from "react-bootstrap";
import React from 'react';
import ReactDOM from 'react-dom';

class FileUploadComponent extends React.Component {

	constructor() {
		super();

		this.uploadFile = this.uploadFile.bind(this);
		this.onFileSelected = this.onFileSelected.bind(this);
	}

	uploadFile(event) {
		event.preventDefault();
		var inputNode = ReactDOM.findDOMNode(this.refs.fileUpload);
		inputNode.click();
	}

	onFileSelected() {
		var inputNode = ReactDOM.findDOMNode(this.refs.fileUpload);
		if(inputNode.files && inputNode.files[0]) {
			let file = inputNode.files[0];
			this.props.onFileSelected(file);
		}
		ReactDOM.findDOMNode(this.refs.form).reset();
	}

	render() {
		return <div>
			<Button bsStyle="primary" onClick={this.uploadFile}>{this.props.title}</Button>
			<form style={{display: "none"}} ref="form"><input ref="fileUpload" type="file"
															  onChange={this.onFileSelected}/></form>
		</div>;
	}
}

export default FileUploadComponent;

