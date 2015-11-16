import { connect } from 'react-redux';

import { History } from 'react-router';

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
var Icon = require('react-fa');

import FileUploadComponent from "../components/FileUploadComponent.js";


export default connect(state => {
	return {
		clientState: state.clientState,
		patches: state.patches,
		selectedPatchId: state.selectedPatchId,
		activePatchId: state.activePatchId,
		uploadProgress: state.uploadProgress,
		config: state.config
	};
})(React.createClass({
			displayName: "Patches",
			mixins: [History],
			contextTypes: {
				actions: React.PropTypes.object.isRequired
			},

			navigateToPatch(patchId) {
				let newUrl = '/patches/' + patchId;
				this.history.pushState(null, newUrl, this.props.location.query);
			},

			renderPatch(patch) {
				let actions = this.context.actions;
				let rowClassName = null;
				let style = null;
				if (patch.id === this.activePatchId) {
					rowClassName = "warning";
					style = {fontWeight: "bold"};
				}
				if (patch.id === this.selectedPatchId) {
					rowClassName = "info";
				}
				return <tr key={patch.id} onClick={this.navigateToPatch.bind(this, patch.id)}
						   className={rowClassName} style={style}>
					<td>{patch.creationDate}</td>
					<td>{patch.name ?
						<span>{patch.name} <span className="text-muted">({patch.revision})</span></span>
						: patch.revision}</td>
					<td>{patch.parentRevision || "-"}</td>
				</tr>;
			},

			render() {
				let actions = this.context.actions;
				let config = this.props.config;
				let patches = this.props.patches || [];
				let selectedPatchId = this.selectedPatchId = this.props.selectedPatchId;
				this.activePatchId = this.props.activePatchId;
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Patches <span className="text-muted">({patches.length})</span></h2>
								<Button onClick={actions.loadPatches}>Refresh</Button>
									<span className="pull-right">
										{this.props.uploadProgress?<span>Uploading...{this.props.uploadProgress.percentComplete}%</span>:
										<FileUploadComponent title="Upload patch" extension=".floto-patch.zip"
															 onFileSelected={(patchFile) => actions.uploadPatch(patchFile)}/>}
								</span>
								{config.patchMode === "create" ?
									<span className="pull-right">
									<Button bsStyle="primary"
											onClick={() => actions.createFullPatch()}>Create
										full patch</Button>
								</span> : null}

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
						<div key={selectedPatchId} style={{flex: 1, paddingLeft: 20, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);

