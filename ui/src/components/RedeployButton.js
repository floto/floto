import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";

let defaultDeploymentMode = "fromRootImage";

let rootStyle = {fontWeight: "bold"};
let baseStyle = {fontWeight: "normal"};

export default React.createClass({
	onExecute(deploymentMode, event) {
		event.stopPropagation();
		this.props.onExecute(deploymentMode);
	},

	render() {
		return <SplitButton bsStyle="primary" bsSize={this.props.size || "xs"}
							onClick={this.onExecute.bind(this, "fromRootImage")}
							title="Redeploy" id="redeploy" disabled={this.props.disabled}>
			<MenuItem onSelect={this.onExecute.bind(this, "fromRootImage")}><span
				style={rootStyle}>From Root Image</span></MenuItem>
			<MenuItem onSelect={this.onExecute.bind(this, "fromBaseImage")}><span
				style={baseStyle}>From Base Image</span></MenuItem>
			<MenuItem onSelect={this.onExecute.bind(this, "containerRebuild")}>Recreate Container</MenuItem>
		</SplitButton>;
	}
});
