import { connect } from 'react-redux';

import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";

export default connect(state => {
	return {config: state.config};
})(React.createClass({
	onExecute(deploymentMode, event) {
		event.stopPropagation();
		this.props.onExecute(deploymentMode);
	},

	render() {
		let defaultDeploymentMode = this.props.config.defaultDeploymentMode || "fromBaseImage";

		let defaultFromBase = defaultDeploymentMode === "fromBaseImage";

		let rootStyle = defaultFromBase ? null : {fontWeight: "bold"};
		let baseStyle = defaultFromBase ? {fontWeight: "bold"} : null;

		return <SplitButton bsStyle="primary" bsSize={this.props.size || "xs"}
							onClick={this.onExecute.bind(this, defaultDeploymentMode)}
							title={this.props.title || "Redeploy"} id="redeploy" disabled={this.props.disabled}>
			<MenuItem onSelect={this.onExecute.bind(this, "fromRootImage")}><span
				style={rootStyle}>From Root Image</span></MenuItem>
			<MenuItem onSelect={this.onExecute.bind(this, "fromBaseImage")}><span
				style={baseStyle}>From Base Image</span></MenuItem>
			<MenuItem onSelect={this.onExecute.bind(this, "containerRebuild")}>Recreate Container</MenuItem>
		</SplitButton>;
	}
}));
