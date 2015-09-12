import { connect } from 'react-redux';

import { Navigation } from 'react-router';


import ContainerGroup from "./ContainerGroup.js";

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
var Icon = require('react-fa');

import * as actions from "../actions/actions.js";

import RedeployButton from "../components/RedeployButton.js";

let containerGroupings = {
	none: {title: "None", groupFn: (containers) => [{containers: containers}]},
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
		containers: state.manifest.containers,
		selectedContainer: state.selectedContainer
	};
})(React.createClass({
			mixins: [Navigation],

			onChangeContainerGrouping(event, grouping) {
				let query = {grouping};
				if (grouping === "none") {
					query = null;
				}
				this.transitionTo(this.props.location.pathname, query);
			},

			render() {
				let containers = this.props.containers || [];
				let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
				let dispatch = this.props.dispatch;
				let containerGroupingKey = (this.props.location.query || {}).grouping || "none";
				let containerGrouping = containerGroupings[containerGroupingKey] || containerGroupings.none;
				let allContainerNames = _.pluck(containers, "name");
				let groups = containerGrouping.groupFn(containers);
				groups = _.sortBy(groups, "title");
				_.forEach(groups, group => {
					group.containerNames = _.pluck(group.containers, "name");
				});
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Containers <span className="text-muted">({containers.length})</span></h2>
								<ButtonGroup>
									<Button>Refresh</Button>
									<RedeployButton disabled={!safetyArmed} size="medium"
													onExecute={(deploymentMode) => actions.redeployContainers(dispatch, allContainerNames, deploymentMode)}/>
									<Button bsStyle="success"
											disabled={!safetyArmed}>Start all</Button>
									<Button bsStyle="danger"
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
								{groups.map((group) =>
								<ContainerGroup group={group} location={this.props.location}/>)}
							</div>
						</div>
						<div style={{flex: 1, paddingLeft: 20, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);







