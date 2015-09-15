import { connect } from 'react-redux';

import { History } from 'react-router';

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
var Icon = require('react-fa');

export default connect(state => {
	return {
		clientState: state.clientState,
		patches: state.patches,
		selectedHost: state.selectedHost
	};
})(React.createClass({
			displayName: "Patches",
			mixins: [History],
			contextTypes: {
				actions: React.PropTypes.object.isRequired
			},


			navigateToPatch(patchId) {
				let newUrl = '/hosts/' + hostName;
				if (this.props.selectedHost) {
					let currentUrl = "/hosts/" + this.props.selectedHost.name;
					newUrl = this.props.location.pathname.replace(currentUrl, newUrl);
				}
				this.history.pushState(null, newUrl, this.props.location.query);
			},

			renderPatch(patch) {
				let actions = this.context.actions;
				let rowClassName = null;
				/*				if(patch === this.selectedHost) {
				 rowClassName = "info";
				 }*/
				return <tr key={patch.name} onClick={this.navigateToPatch.bind(this, patch.name)}
						   className={rowClassName}>
					<td>{patch.creationDate}</td>
					<td>{patch.revision}</td>
					<td>{patch.parentRevision || "-"}</td>
				</tr>;
			},

			render() {
				console.log(this.props);
				let actions = this.context.actions;
				let patches = this.props.patches || [];
				let selectedPatch = this.selectedPatch = this.props.selectedPatch || {};
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Patches <span className="text-muted">({patches.length})</span></h2>
								<Button onClick={actions.loadPatches}>Refresh</Button>
								<span className="pull-right">
									<Button bsStyle="primary"
											onClick={() => actions.redeployHosts()}>Create
										full patch</Button>
								</span>
							</div>
							<div style={{flex: "1 1 auto", overflowY: "scroll"}}>
								<Table bordered striped hover condensed style={{cursor: "pointer"}}>
									<thead>
									<tr>
										<th style={{width: "12em"}}>Created</th>
										<th>Revision</th>
										<th>Parent</th>
									</tr>
									</thead>
									<tbody>
									{patches.map(this.renderPatch)}
									</tbody>
								</Table>
							</div>
						</div>
						<div key={selectedPatch.name} style={{flex: 1, paddingLeft: 20, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);

