import { connect } from 'react-redux';

import { Navigation } from 'react-router';

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
var Icon = require('react-fa');

import * as actions from "../actions/actions.js";

import RedeployButton from "../components/RedeployButton.js";

let containerGroupings = {
	none: "None",
	host: "By host",
	image: "By image"
};

export default connect(state => {
	return {
		clientState: state.clientState,
		containers: state.manifest.containers,
		selectedContainer: state.selectedContainer
	}
})(React.createClass({
			mixins: [Navigation],

			onChangeContainerGrouping(event, grouping) {
				let query = {grouping};
				if (grouping === "none") {
					query = null;
				}
				this.transitionTo(this.props.location.pathname, query)
			},

			navigateToContainer(containerName) {
				let newUrl = '/containers/' + containerName;
				if (this.props.selectedContainer) {
					let currentUrl = "/containers/" + this.props.selectedContainer.name;
					newUrl = this.props.location.pathname.replace(currentUrl, newUrl);
				}
				this.transitionTo(newUrl, this.props.location.query);
			},

			renderContainer(container) {
				let safetyArmed = this.safetyArmed;
				let className = null;
				if(this.props.selectedContainer === container) {
					className = "info";
				}
				return <tr key={container.name} className={className}
					onClick={this.navigateToContainer.bind(this, container.name)}>
					<td><Label bsStyle='default'>{container.state || "unknown" }</Label></td>
					<td>
						<div style={{width: 100}}><RedeployButton disabled={!safetyArmed}
																  onExecute={(deploymentMode) => actions.redeployContainers(dispatch, [container.name], deploymentMode)}/>
						</div>
					</td>
					<td><Button bsStyle="success" bsSize="xs"
								disabled={!safetyArmed}>Start</Button></td>
					<td><Button bsStyle="danger" bsSize="xs"
								disabled={!safetyArmed}>Stop</Button></td>
					<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed}>Purge
						Data</Button></td>
					<td style={{whiteSpace: "nowrap"}}>{container.config.webUrl ?
						<a href={container.config.webUrl}><Icon name="globe"/>&nbsp;&nbsp;Web UI</a> : null}</td>
					<td style={{width: "100%"}}>{container.name}<span
						className="text-muted pull-right">{container.config.version}</span></td>
				</tr>
			},

			render() {
				let containers = this.props.containers || [];
				let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
				let dispatch = this.props.dispatch;
				let containerGrouping = (this.props.location.query || {}).grouping || "none";
				let containerGroupingName = containerGroupings[containerGrouping];
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Containers</h2>
								<ButtonGroup>
									<Button>Refresh</Button>
									<RedeployButton disabled={!safetyArmed} size="medium"
													onExecute={(deploymentMode) => actions.redeployContainers(dispatch, [container.name], deploymentMode)}/>
									<Button bsStyle="success"
											disabled={!safetyArmed}>Start all</Button>
									<Button bsStyle="danger"
											disabled={!safetyArmed}>Stop all</Button>
								</ButtonGroup>
								<span className="pull-right">Group by:&nbsp;&nbsp;&nbsp;
									<DropdownButton bsStyle="default" title={containerGroupingName}
													id="container-grouping"
													disabled={!safetyArmed} onSelect={this.onChangeContainerGrouping}>
										{_.map(containerGroupings, (grouping, key) =>
											<MenuItem key={key} eventKey={key}><span
												style={{fontWeight: key === containerGrouping ? "bold": "normal"}}>{grouping}</span></MenuItem>)}
									</DropdownButton>
								</span>
							</div>
							<div style={{flex: "1 1 auto", overflowY: "scroll"}}>
								<Table bordered striped hover condensed style={{cursor: "pointer"}}>
									<tbody>
									{containers.map(this.renderContainer)}
									</tbody>
								</Table>
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







