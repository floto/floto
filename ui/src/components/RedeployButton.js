import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";

let defaultDeploymentMode = "fromRootImage";

let rootStyle = {fontWeight: "bold"};
let baseStyle = {fontWeight: "normal"};

export default React.createClass({
	render() {
		let onExecute = this.props.onExecute;
		return <SplitButton bsStyle="primary" bsSize={this.props.size || "xs"} onClick={onExecute.bind(null, "fromRootImage")}
					 title="Redeploy" id="redeploy" disabled={this.props.disabled}>
			<MenuItem onSelect={onExecute.bind(null, "fromRootImage")}><span style={rootStyle}>From Root Image</span></MenuItem>
			<MenuItem onSelect={onExecute.bind(null, "fromBaseImage")}><span style={baseStyle}>From Base Image</span></MenuItem>
			<MenuItem onSelect={onExecute.bind(null, "containerRebuild")}>Recreate Container</MenuItem>
		</SplitButton>;
	}
});
