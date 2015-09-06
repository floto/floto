import { connect } from 'react-redux';

import { Navigation } from 'react-router';

import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";
var Icon = require('react-fa');

import * as actions from "../actions/actions.js";

import RedeployButton from "../components/RedeployButton.js";

export default connect(state => {
	return {containers: state.manifest.containers, clientState: state.clientState}
})(React.createClass({
			mixins: [Navigation],

			render() {
				let containers = this.props.containers || [];
				let safetyArmed = this.props.clientState.safetyArmed;
				let dispatch = this.props.dispatch;
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%"}}>
							<h2>Containers</h2>
							<Table bordered striped hover condensed style={{cursor: "pointer"}}>
								<tbody>
								{containers.map((container) =>
									<tr key={container.name} onClick={() => this.transitionTo('/containers/'+container.name)}>
										<td><Label bsStyle='default'>{container.state || "unknown" }</Label></td>
										<td>
											<div style={{width: 100}}><RedeployButton disabled={!safetyArmed} onExecute={(deploymentMode) => actions.redeployContainers(dispatch, [container.name], deploymentMode)}/></div>
										</td>
										<td><Button bsStyle="success" bsSize="xs" disabled={!safetyArmed}>Start</Button></td>
										<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed}>Stop</Button></td>
										<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed}>Purge Data</Button></td>
										<td style={{whiteSpace: "nowrap"}}>{container.config.webUrl ? <a href={container.config.webUrl}><Icon name="globe" />&nbsp;&nbsp;Web UI</a> : null}</td>
										<td style={{width: "100%"}}>{container.name}<span className="text-muted pull-right">{container.config.version}</span></td>
									</tr>)}
								</tbody>
							</Table>
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







