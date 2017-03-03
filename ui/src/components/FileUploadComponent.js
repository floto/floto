import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";


export default React.createClass({

	uploadFile(event) {
		event.preventDefault();
		var inputNode = ReactDOM.findDOMNode(this.refs.fileUpload);
		inputNode.click();
	},
	onFileSelected() {
		var inputNode = ReactDOM.findDOMNode(this.refs.fileUpload);
		if(inputNode.files && inputNode.files[0]) {
			let file = inputNode.files[0];
			this.props.onFileSelected(file);
		}
		ReactDOM.findDOMNode(this.refs.form).reset();
	},

	render() {
		return <div>
			<Button bsStyle="primary" onClick={this.uploadFile}>{this.props.title}</Button>
			<form style={{display: "none"}} ref="form"><input ref="fileUpload" type="file"
																 onChange={this.onFileSelected}/></form>
		</div>;
	}
});

