import { connect } from 'react-redux';
import { Navigation } from 'react-router';
import DebounceInput from 'react-debounce-input';

import ContainerGroup from "./ContainerGroup.js";

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup, Panel, Input} from "react-bootstrap";
import { Icon } from 'react-fa';

import RedeployButton from "../components/RedeployButton.js";

let containerGroupings = {
	none: {title: "None", groupFn: (containers) => [{containers: containers, id: "all"}]},
	host: {
		title: "By host", groupFn: (containers) => {
			let result = {};
			containers.forEach(function (container) {
				var hostGroup = result[container.host] || {title: container.host, containers: []};
				result[container.host] = hostGroup;
				hostGroup.containers.push(container);
			});
			return _.values(result);
		}
	},
	image: {
		title: "By image", groupFn: (containers) => {
			let result = {};
			containers.forEach(function (container) {
				var imageGroup = result[container.image] || {title: container.image, containers: []};
				result[container.image] = imageGroup;
				imageGroup.containers.push(container);
			});
			return _.values(result);
		}
	}
};

export default connect(state => {
	return {
		manifestError: state.manifestError,
		clientState: state.clientState,
		containers: state.containers,
		containerFilter: state.containerFilter,
		unmanagedContainers: state.unmanagedContainers,
		selectedContainer: state.selectedContainer
	};
})(React.createClass({
		displayName: "Containers",
		mixins: [Navigation],
		contextTypes: {
			actions: React.PropTypes.object.isRequired,
			router: React.PropTypes.object.isRequired
		},

		onChangeContainerGrouping(event, grouping) {
			let query = this.props.location.query || {};
			if (grouping === "none") {
				delete query.grouping;
			}
			else {
				query.grouping = grouping;
			}
			this.context.router.push({pathname: this.props.location.pathname, query: query});
		},

		onChangeContainerFilter(event) {
			let query = this.props.location.query || {};
			query.containerFilter = event.target.value.trim();
			if(query.containerFilter === "") {
				delete query.containerFilter;
			}
			this.context.router.push({pathname: this.props.location.pathname, query: query});
		},

		render() {
			let actions = this.context.actions;
			let containers = this.props.containers || [];
			let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
			let query = (this.props.location.query || {});
            let containerGroupingKey = query.grouping || "none";
			let containerFilter = query.containerFilter || "";
			let containerGrouping = containerGroupings[containerGroupingKey] || containerGroupings.none;
			let allContainerNames = _.map(containers, (container) => container.name);
			let groups = containerGrouping.groupFn(containers);
			let unmanagedContainers = this.props.unmanagedContainers;
			groups = _.sortBy(groups, "title");
			let unmanagedContainersComponent = null;
			let selectedContainer = this.props.selectedContainer || {};
			if (containerGroupingKey === "none" && unmanagedContainers && unmanagedContainers.length > 0) {
				groups[0].title = "Managed containers";
				unmanagedContainersComponent = <div>
					<h4>Unmanaged Containers<span className="text-muted"> ({unmanagedContainers.length})</span></h4>
					<Table bordered striped hover condensed style={{cursor: "pointer"}}>
						<tbody>
						{unmanagedContainers.map((container) => <tr key={container.name} className="warning">
							<td><Label bsStyle="warning">{container.state.status || "unknown" }</Label></td>
							<td><Button bsStyle="danger" bsSize="xs"
										onClick={actions.destroyContainers.bind(null, container.name, container.state.hostName)}>Destroy</Button>
							</td>
							<td style={{width: "100%"}}>{container.name}@{container.state.hostName}</td>
						</tr>)}
						</tbody>
					</Table>
				</div>;
			}
			let containerFilterError = null;
			let containerFilterRegex = null;
			try {
				containerFilterRegex = new RegExp(containerFilter, "i");
			} catch(error) {
				console.log(error);
				containerFilterError = ""+error;
			}

			let filteredContainerCount = 0;
			let filteredContainerNames = [];
			let controlledContainerNames = [];
			let changedContainerNames = [];
			let startableContainerNames = [];
			let stoppableContainerNames = [];
			
			_.forEach(groups, group => {
				
				let groupControlledContainerNames = [];
				let groupStartableContainerNames = [];
				let groupStoppableContainerNames = [];
				
				group.containers = _.sortBy(group.containers, "name");
				group.totalCount = group.containers.length;
				if(containerFilterRegex !== null) {
					group.containers = _.filter(group.containers, (container) => containerFilterRegex.test(container.name));
				}
				group.changedContainerNames = [];
				_.forEach(group.containers, (container) => {
					if (!container.externalContainer) {
						controlledContainerNames.push(container.name);
						groupControlledContainerNames.push(container.name);
						if (container.state.needsRedeploy) {
							changedContainerNames.push(container.name);
							group.changedContainerNames.push(container.name);
						}
					}
					if(container.startable == null || container.startable == true){
						startableContainerNames.push(container.name);
						groupStartableContainerNames.push(container.name);
					}
					if(container.stoppable == null || container.stoppable == true){
						stoppableContainerNames.push(container.name);
						groupStoppableContainerNames.push(container.name);
					}
				});
				group.containerNames = _.map(group.containers, (container) => container.name);
				group.controlledContainerNames = groupControlledContainerNames;
				group.startableContainerNames = groupStartableContainerNames;
				group.stoppableContainerNames = groupStoppableContainerNames;
			filteredContainerNames = filteredContainerNames.concat(group.containerNames);
			filteredContainerCount += group.containers.length;
			});

			groups = _.filter(groups, (group) => group.containers.length > 0);


			if (this.props.manifestError) {
				return <div style={{height: "100%", marginTop: 20, marginRight: 20}}>
					<Panel header="Manifest compilation error" bsStyle="danger">
						{this.props.manifestError.message}
					</Panel>
				</div>;
			}
			let containerCount = containers.length;
			let containerCountName = "all";
			let controlledCountName = "all";
			let startableCountName = "all";
			let stoppableCountName = "all";
			if(filteredContainerCount !== containers.length) {
                containerCount = filteredContainerCount  + "/" + containerCount;
                containerCountName = filteredContainerCount;
            }
			if(controlledContainerNames.length !== filteredContainerCount) {
				controlledCountName = controlledContainerNames.length;
			}
			if(startableContainerNames.length !== filteredContainerCount) {
				startableCountName = startableContainerNames.length;
			}
			if(stoppableContainerNames.length !== filteredContainerCount) {
				stoppableCountName = stoppableContainerNames.length;
			}

			var Resize         = require('react-resize-layout').Resize;
			var ResizeHorizon  = require('react-resize-layout').ResizeHorizon;

			let buttonStyle = {width: "100px"};
			return <div style={{height: "100%"}}>
				<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
					<Resize handleWidth="3px" handleColor="#CCC">
						<ResizeHorizon width="850px" minWidth="300px">
							<div style={{flex: "1 1 auto", width: "100%", height: "100%", display:"flex", flexDirection: "column"}}>
								<div style={{flex: "0 0 auto", marginBottom: "10px", marginRight: "10px"}}>
									<h2>Containers <span className="text-muted">({containerCount})</span>
																		<span className="pull-right"><small>Grouping:&nbsp;&nbsp;&nbsp;</small>
											<DropdownButton bsStyle="default" title={containerGrouping.title}
															id="container-grouping"
															onSelect={this.onChangeContainerGrouping}>
												{_.map(containerGroupings, (grouping, key) =>
													<MenuItem key={key} eventKey={key}><span
														style={{fontWeight: key === containerGroupingKey ? "bold": "normal"}}>{grouping.title}</span></MenuItem>)}
											</DropdownButton>
										</span>

									</h2>
									<ButtonGroup>
										<Button onClick={actions.loadContainerStates}>Refresh</Button>
										<RedeployButton style={{width: "150px"}} disabled={!safetyArmed || changedContainerNames.length < 1} title={"Redeploy " + changedContainerNames.length + " changed"}
														onExecute={(deploymentMode) => actions.redeployContainers( changedContainerNames, deploymentMode)}/>
										<RedeployButton style={buttonStyle} disabled={!safetyArmed || controlledContainerNames.length < 1} title={"Redeploy " + controlledCountName}
														onExecute={(deploymentMode) => actions.redeployContainers( controlledContainerNames, deploymentMode)}/>
										<Button bsStyle="success" onClick={() => actions.startContainers(startableContainerNames)}
												disabled={!safetyArmed || startableContainerNames.length < 1} style={buttonStyle} >Start {startableCountName}</Button>
										<Button bsStyle="danger" onClick={() => actions.stopContainers(stoppableContainerNames)}
												disabled={!safetyArmed || stoppableContainerNames.length < 1} style={buttonStyle}>Stop {stoppableCountName}</Button>
										<span className={containerFilterError?"has-warning":""}>
										<DebounceInput
											style={{display: "inline-block", width: "160px", marginLeft: "5px"}}
											type="text"
											placeholder="Filter containers"
											title={containerFilterError}
											value={containerFilter}
											debounceTimeout={(containers.length > 500)? 500 : 0}
											onChange={this.onChangeContainerFilter}
											className="form-control"
										/>
										</span>
									</ButtonGroup>
								</div>
								<div style={{flex: "1 1 auto", overflowY: "scroll"}}>
									{unmanagedContainersComponent}
									{groups.map((group) =>
										<ContainerGroup key={group.title || group.id} group={group}
														location={this.props.location}/>)}
								</div>
							</div>
						</ResizeHorizon>
						<ResizeHorizon minWidth="300px">
							<div key={selectedContainer.name}
								 style={{flex: "1 1 auto", width: "100%", paddingLeft: 20, height: "100%"}}>
								{this.props.children}
							</div>
						</ResizeHorizon>
					</Resize>
				</div>
			</div>;

		}
	}
	)
);






