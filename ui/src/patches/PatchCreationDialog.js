import {Button, FormGroup, FormControl, ControlLabel, Modal} from "react-bootstrap";
import React from 'react';

class PatchCreationDialog extends React.Component {

	constructor() {
		super();

		this.state = {
			name: "",
			comment: ""
		}

		this.onCreate = this.onCreate.bind(this);
		this.onCancel = this.onCancel.bind(this);
	}

	onCreate() {
		this.props.done({name: this.state.name, comment: this.state.comment});
	}

	onCancel() {
		this.props.done(null);
	}

	/*
	 <FormGroup
	 type="text"
	 label="Patch name"
	 value={this.state.name}
	 placeholder="Enter patch name"
	 onChange={(event) => this.setState({name: event.target.value})}
	 autoFocus="{true}"
	 />
	 */

	render() {
		return <Modal {...this.props} onHide={this.onCancel}>
			<Modal.Header closeButton>
				<Modal.Title>Create patch</Modal.Title>
			</Modal.Header>
			<Modal.Body>
				<FormGroup controlId="PatchNameInput">
					<ControlLabel>Patch name</ControlLabel>
					<FormControl type="text"
								 value={this.state.name}
								 placeholder="Enter patch name"
								 onChange={(event) => this.setState({name: event.target.value})}
								 autoFocus="{true}"/>
				</FormGroup>
				<FormGroup controlId="PatchCommentInput">
					<ControlLabel>Patch comments</ControlLabel>
					<FormControl componentClass="textarea" label="Patch comments" placeholder="Enter patch comments"
								 value={this.state.comment}
								 onChange={(event) => this.setState({comment: event.target.value})}
					/>
				</FormGroup>
			</Modal.Body>
			<Modal.Footer>
				<Button bsStyle="success" onClick={this.onCreate}>Create Patch</Button>
				<Button onClick={this.onCancel}>Cancel</Button>
			</Modal.Footer>
		</Modal>;
	}
}

export default PatchCreationDialog;
