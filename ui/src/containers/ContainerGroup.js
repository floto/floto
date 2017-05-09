import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
import { connect } from 'react-redux';
import { Icon } from 'react-fa';

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
		let externalContainer = false;
		let startable = false;
		let stoppable = false;
		let purgeable = false;
		if(container.unmanaged) {
			className = "warning";
		}
		if (this.props.selectedContainer === container) {
			className = "info";
		}
		if(container.externalContainer){
			externalContainer = true;
			container.state.needsRedeploy = false;
		}
		if(container.stoppable == null || container.stoppable == true){
			stoppable = true;
		}
		if(container.startable == null || container.startable == true){
			startable = true;
		}
		if(container.purgeable == null || container.purgeable == true){
			purgeable = true;
		}
		var containerState = container.state;
        let status = containerState.status;
		let labelStyle = labelStyleMapping[status] || "default";
		let config = container.config || {};
		return <tr key={container.name} className={className}
				   onClick={this.navigateToContainer.bind(this, container.name)}>
			<td><Label bsStyle={labelStyle}>{status || "unknown" }</Label></td>
			<td>
				<div style={{width: 100}}><RedeployButton disabled={!safetyArmed || externalContainer} size="xs"
														  bsStyle={containerState.needsRedeploy?"primary": "default"}
														  onExecute={(deploymentMode) => actions.redeployContainers([container.name], deploymentMode)}/>
				</div>
			</td>
			<td><div style={{width: 50}}>
				{status === "running"?<Button bsStyle="success" bsSize="xs" onClick={actions.restartContainers.bind(null, [container.name])}
						disabled={!(safetyArmed && startable)}>Restart</Button>:
				<Button bsStyle="success" bsSize="xs" onClick={actions.startContainers.bind(null, [container.name])}
						disabled={!(safetyArmed && startable)}>Start</Button>}
				</div>
			</td>
			<td><Button bsStyle="danger" bsSize="xs" onClick={actions.stopContainers.bind(null, [container.name])}
						disabled={!(safetyArmed && stoppable)}>Stop</Button></td>
			<td><Button bsStyle="danger" bsSize="xs" onClick={actions.purgeContainerData.bind(null, [container.name])}
						disabled={!(safetyArmed && purgeable)}>Purge
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
		let controlledContainerNames = group.controlledContainerNames;
		let startableContainerNames = group.startableContainerNames;
		let stoppableContainerNames = group.stoppableContainerNames;
		let controlledContainerCountName = "all";
		let startableContainerCountName = "all";
		let stoppableContainerCountName = "all";
		let containerTitleCount = containers.length;
		if(group.totalCount !== controlledContainerNames.length) {
			controlledContainerCountName = controlledContainerNames.length;
			containerTitleCount = containers.length + "/" + group.totalCount;
		}
		if(group.totalCount !== startableContainerNames.length){
			startableContainerCountName = startableContainerNames.length;
		}
		if(group.totalCount !== stoppableContainerNames.length){
			stoppableContainerCountName = stoppableContainerNames.length;
		}
		let changedContainerNames = group.changedContainerNames;
		let buttonStyle = {width: "100px"};

		let titleComponent = null;
		if (group.title) {
			
			titleComponent = <h4>{group.title} <span className="text-muted">({containerTitleCount})</span><span className="pull-right"><ButtonGroup bsSize='small'>
				<RedeployButton disabled={!safetyArmed || changedContainerNames.length < 1} size="small" title={"Redeploy " + changedContainerNames.length + " changed"}
								onExecute={(deploymentMode) => actions.redeployContainers(changedContainerNames, deploymentMode)} style={{width: "140px"}}/>
				<RedeployButton disabled={!safetyArmed || controlledContainerNames.length < 1} size="small" title={"Redeploy " + controlledContainerCountName}
								onExecute={(deploymentMode) => actions.redeployContainers(controlledContainerNames, deploymentMode)} style={buttonStyle}/>
				<Button bsStyle="success" onClick={() => actions.startContainers(startableContainerNames)}
						disabled={!safetyArmed || startableContainerNames.length < 1} style={buttonStyle}>Start {startableContainerCountName}</Button>
				<Button bsStyle="danger" onClick={() => actions.stopContainers(stoppableContainerNames)}
						disabled={!safetyArmed || stoppableContainerNames.length < 1} style={buttonStyle}>Stop {stoppableContainerCountName}</Button>
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

