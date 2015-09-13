import { connect } from 'react-redux';

import { Navigation } from 'react-router';


import ContainerGroup from "./ContainerGroup.js";

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
var Icon = require('react-fa');

import RedeployButton from "../components/RedeployButton.js";

let containerGroupings = {
	none: {title: "None", groupFn: (containers) => [{containers: containers, id: "all"}]},
	host: {title: "By host", groupFn: (containers) => {
		let result = {};
		containers.forEach(function (container) {
			var hostGroup = result[container.host] || {title: container.host, containers: []};
			result[container.host] = hostGroup;
			hostGroup.containers.push(container);
		});
		return _.values(result);
	}},
	image: {title: "By image", groupFn: (containers) => {
		let result = {};
		containers.forEach(function (container) {
			var imageGroup = result[container.image] || {title: container.image, containers: []};
			result[container.image] = imageGroup;
			imageGroup.containers.push(container);
		});
		return _.values(result);
	}}
};

export default connect(state => {
	return {
		clientState: state.clientState,
		containers: state.containers,
		unmanagedContainers: state.unmanagedContainers,
		selectedContainer: state.selectedContainer
	};
})(React.createClass({
			displayName: "Containers",
			mixins: [Navigation],
			contextTypes: {
				actions: React.PropTypes.object.isRequired
			},

			onChangeContainerGrouping(event, grouping) {
				let query = {grouping};
				if (grouping === "none") {
					query = null;
				}
				this.transitionTo(this.props.location.pathname, query);
			},

			render() {
				let actions = this.context.actions;
				let containers = this.props.containers || [];
				let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
				let containerGroupingKey = (this.props.location.query || {}).grouping || "none";
				let containerGrouping = containerGroupings[containerGroupingKey] || containerGroupings.none;
				let allContainerNames = _.pluck(containers, "name");
				let groups = containerGrouping.groupFn(containers);
				let unmanagedContainers = this.props.unmanagedContainers;
				groups = _.sortBy(groups, "title");
				let unmanagedContainersComponent = null;
				let selectedContainer = this.props.selectedContainer || {};
				if(containerGroupingKey === "none" && unmanagedContainers && unmanagedContainers.length > 1) {
					groups[0].title = "Managed containers";
					unmanagedContainersComponent = <div>
						<h4>Unmanaged Containers<span className="text-muted"> ({unmanagedContainers.length})</span></h4>
						<Table bordered striped hover condensed style={{cursor: "pointer"}}>
							<tbody>
							{unmanagedContainers.map((container) => <tr key={container.name} className="warning">
								<td><Label bsStyle="warning">{container.state.status || "unknown" }</Label></td>
								<td><Button bsStyle="danger" bsSize="xs" onClick={actions.destroyContainers.bind(null, container.name, container.state.hostName)}>Destroy</Button></td>
								<td style={{width: "100%"}}>{container.name}@{container.state.hostName}</td>
							</tr>)}
							</tbody>
						</Table>
					</div>;
				}
				_.forEach(groups, group => {
					group.containerNames = _.pluck(group.containers, "name");
				});
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Containers <span className="text-muted">({containers.length})</span></h2>
								<ButtonGroup>
									<Button onClick={actions.loadContainerStates}>Refresh</Button>
									<RedeployButton disabled={!safetyArmed} size="medium"
													onExecute={(deploymentMode) => actions.redeployContainers( allContainerNames, deploymentMode)}/>
									<Button bsStyle="success" onClick={() => actions.startContainers(allContainerNames)}
											disabled={!safetyArmed}>Start all</Button>
									<Button bsStyle="danger" onClick={() => actions.stopContainers(allContainerNames)}
											disabled={!safetyArmed}>Stop all</Button>
								</ButtonGroup>
								<span className="pull-right">Grouping:&nbsp;&nbsp;&nbsp;
									<DropdownButton bsStyle="default" title={containerGrouping.title}
													id="container-grouping"
													disabled={!safetyArmed} onSelect={this.onChangeContainerGrouping}>
										{_.map(containerGroupings, (grouping, key) =>
											<MenuItem key={key} eventKey={key}><span
												style={{fontWeight: key === containerGroupingKey ? "bold": "normal"}}>{grouping.title}</span></MenuItem>)}
									</DropdownButton>
								</span>
							</div>
							<div style={{flex: "1 1 auto", overflowY: "scroll"}}>
								{unmanagedContainersComponent}
								{groups.map((group) =>
								<ContainerGroup key={group.title || group.id} group={group} location={this.props.location}/>)}
							</div>
						</div>
						<div key={selectedContainer.name} style={{flex: 1, paddingLeft: 20, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);




