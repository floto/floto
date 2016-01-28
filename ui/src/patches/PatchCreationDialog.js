import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, Input, ButtonGroup, Modal} from "react-bootstrap";

export default React.createClass({

	getInitialState() {
		return {
			name: "",
			comment: ""
		};
	},

	onCreate() {
		this.props.done({name: this.state.name, comment: this.state.comment});
	},

	onCancel() {
		this.props.done(null);
	},

	render() {
		return <Modal {...this.props} onHide={this.onCancel}>
			<Modal.Header closeButton>
				<Modal.Title>Create patch</Modal.Title>
			</Modal.Header>
			<Modal.Body>
				<Input
					type="text"
					label="Patch name"
					value={this.state.name}
					placeholder="Enter patch name"
					onChange={(event) => this.setState({name: event.target.value})}
					autoFocus="{true}"
				/>
				<Input type="textarea" label="Patch comments" placeholder="Enter patch comments"
					   value={this.state.comment}
					   onChange={(event) => this.setState({comment: event.target.value})}
				/>
			</Modal.Body>
			<Modal.Footer>
				<Button bsStyle="success" onClick={this.onCreate}>Create Patch</Button>
				<Button onClick={this.onCancel}>Cancel</Button>
			</Modal.Footer>
		</Modal>;
	}
});
