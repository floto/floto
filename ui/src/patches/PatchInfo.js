import { connect } from 'react-redux';

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
import TimeAgo from "react-timeago";
import Icon from 'react-fa';

import {formatBytes} from "../util/formatting.js";

export default connect(state => {
	return {
		selectedPatch: state.selectedPatch,
		config: state.config
	};
})(React.createClass({
			displayName: "Patches",
			contextTypes: {
				actions: React.PropTypes.object.isRequired
			},


			render() {
				let actions = this.context.actions;
				let config = this.props.config;
				let patch = this.props.selectedPatch;
				if (!patch) {
					return null;
				}
				return <div style={{padding: "5px"}}>
					<h2>{patch.name || patch.revision}</h2>
					<span className="text-muted">{patch.id}</span>
					<br/>
					{config.patchMode === "apply" ?
					<Button bsStyle="warning"
							onClick={() => actions.activatePatch(patch.id)}>Activate patch</Button>:
						<Button bsStyle="success"
								onClick={() => actions.downloadPatch(patch.id)}><Icon name="download" />&nbsp;&nbsp;Download patch</Button>}
					{config.patchMode === "create" ?
					<span className="pull-right">
						<Button bsStyle="primary"
								onClick={() => actions.createIncrementalPatch(patch.id)}>Create incremental patch</Button>
					</span>:null}
					<br/>
					<br/>
					<Table striped condensed style={{tableLayout: "fixed"}}>
						<colgroup>
							<col style={{width: "14em"}}/>
							<col style={{width: "100%"}}/>
						</colgroup>
						<tbody>
						<tr>
							<td>Created:</td>
							<td>{patch.creationDate} <span className="text-muted">(<span><TimeAgo date={patch.creationDate}/></span>)</span>
							</td>
						</tr>
						<tr>
							<td>Revision:</td>
							<td>{patch.revision}</td>
						</tr>
						<tr>
							<td>Parent name:</td>
							<td>{patch.parentName || '-'}</td>
						</tr>
						<tr>
							<td>Parent revision:</td>
							<td>{patch.parentRevision || '-'}{patch.parentId ? <span
								className="text-muted"><br /> ({patch.parentId})</span> : null}</td>
						</tr>
						<tr>
							<td>Patch size:</td>
							<td>{formatBytes(patch.patchSize)}</td>
						</tr>
						<tr>
							<td>Number of image layers:</td>
							<td>{patch.containedImageIds.length} contained / {patch.requiredImageIds.length} total</td>
						</tr>
						</tbody>
					</Table>
					{patch.comment?
						<div>
					<h4>Comment</h4>
					{patch.comment}
							</div>:null}
				</div>;

			}
		}
	)
);

