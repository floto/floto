import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
import { connect } from 'react-redux';
var Icon = require('react-fa');

import RedeployButton from "../components/RedeployButton.js";

const labelStyleMapping = {
	running: "success",
	stopped: "warning",
	"not-there": "danger"
};

export default connect(state => {
	return {
		clientState: state.clientState,
		selectedContainer: state.selectedContainer
	};
})(React.createClass({
	displayName: "ContainerGroup",
	contextTypes: {
		actions: React.PropTypes.object.isRequired,
		router: React.PropTypes.object.isRequired
	},

	navigateToContainer(containerName, event) {
		if(event.button === 1) {
			return; // ignore middle click for navigation
		}
		let newUrl = '/containers/' + containerName;
		if (this.props.selectedContainer) {
			let currentUrl = "/containers/" + this.props.selectedContainer.name;
			newUrl = this.props.location.pathname.replace(currentUrl, newUrl);
		}
		this.context.router.push({pathname: newUrl, query: this.props.location.query});
	},

	renderContainer(container) {
		let actions = this.context.actions;
		let safetyArmed = this.safetyArmed;
		let className = null;
		let unmanaged = container.unmanaged;
		if(container.unmanaged) {
			className = "warning";
		}
		if (this.props.selectedContainer === container) {
			className = "info";
		}
		var containerState = container.state;
        let status = containerState.status;
		let labelStyle = labelStyleMapping[status] || "default";
		let config = container.config || {};
		return <tr key={container.name} className={className}
				   onClick={this.navigateToContainer.bind(this, container.name)}>
			<td><Label bsStyle={labelStyle}>{status || "unknown" }</Label></td>
			<td>
				<div style={{width: 100}}><RedeployButton disabled={!safetyArmed} size="xs"
														  bsStyle={containerState.needsRedeploy?"primary": "default"}
														  onExecute={(deploymentMode) => actions.redeployContainers([container.name], deploymentMode)}/>
				</div>
			</td>
			<td><div style={{width: 50}}>
				{status === "running"?<Button bsStyle="success" bsSize="xs" onClick={actions.restartContainers.bind(null, [container.name])}
						disabled={!safetyArmed}>Restart</Button>:
				<Button bsStyle="success" bsSize="xs" onClick={actions.startContainers.bind(null, [container.name])}
						disabled={!safetyArmed}>Start</Button>}
				</div>
			</td>
			<td><Button bsStyle="danger" bsSize="xs" onClick={actions.stopContainers.bind(null, [container.name])}
						disabled={!safetyArmed}>Stop</Button></td>
			<td><Button bsStyle="danger" bsSize="xs" onClick={actions.purgeContainerData.bind(null, [container.name])}
						disabled={!safetyArmed}>Purge
				Data</Button></td>
			<td style={{whiteSpace: "nowrap"}}><div style={{width: 70}}>{config.webUrl ?
				<a href={container.config.webUrl}><Icon name="globe"/>&nbsp;&nbsp;{config.webTitle || "Web UI"}</a> : null}</div></td>
			<td style={{width: "100%"}}>{config.icon ?
				<Icon name={config.icon} style={{paddingRight: 5}}/> : null}{container.name}<span
				className="text-muted">@{container.host}</span><span
				className="text-muted pull-right">{config.version}</span></td>
		</tr>;
	},

	render() {
		let actions = this.context.actions;
		let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
		var group = this.props.group;
		let containers = group.containers;
		let titleComponent = null;
		if (group.title) {
			titleComponent = <h4>{group.title} <span className="text-muted">({containers.length})</span><span className="pull-right"><ButtonGroup bsSize='small'>
				<RedeployButton disabled={!safetyArmed} size="small" title="Redeploy all"
								onExecute={(deploymentMode) => actions.redeployContainers(group.containerNames, deploymentMode)}/>
				<Button bsStyle="success" onClick={() => actions.startContainers(group.containerNames)}
						disabled={!safetyArmed}>Start all</Button>
				<Button bsStyle="danger" onClick={() => actions.stopContainers(group.containerNames)}
						disabled={!safetyArmed}>Stop all</Button>
			</ButtonGroup>
			</span>
			</h4>;
		}
		return <div>
			{titleComponent}
			<Table bordered striped hover condensed style={{cursor: "pointer"}}>
				<tbody>
				{containers.map(this.renderContainer)}
				</tbody>
			</Table>
		</div>;
	}

}));

