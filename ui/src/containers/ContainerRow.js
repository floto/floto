//'use-strict';

import {Label, Button} from "react-bootstrap";
import { connect } from 'react-redux';
import {Icon} from 'react-fa';

import RedeployButton from "../components/RedeployButton.js";
//import ReactResizeDetector from 'react-resize-detector';
import VisibilitySensor from 'react-visibility-sensor';

const labelStyleMapping = {
	running: "success",
	stopped: "warning",
	"not-there": "danger"
};

export default connect(state => {
	return {};
})(React.createClass({
	propTypes: {
		key: React.PropTypes.string,
		isSelected: React.PropTypes.bool,
		container: React.PropTypes.object,
		location: React.PropTypes.object,
		safetyArmed: React.PropTypes.bool
	},
	displayName: "ContainerRow",
	contextTypes: {
		actions: React.PropTypes.object.isRequired,
		router: React.PropTypes.object.isRequired
	},

	getInitialState() {
		return {
			isVisible: true
		};
	},

	shouldComponentUpdate(nextProps, nextState) {

		if( this.props.isSelected !== nextProps.isSelected &&
		this.props.location !== nextProps.location ) {

			console.log( this.props.container.name + " props changed" );

			return true;
		}

		else if( this.state !== nextState ) {

			console.log( this.props.container.name + " state changed" );

			return true;
		}

		else {

			return false;
		}
	},

	handleVisibilityChange(isVisible) {

		//const realHeight = document.getElementById(this.createRowId()).clientHeight;

		/*
		this.setState({
		 	isVisible: isVisible,
			invisibleHeight: realHeight
		 });
		 */
	},

	createRowId() {
		return "container_id_" + this.props.container.name;
	},

	render() {
		console.log( "render " + this.props.container.name);

		const myContainer = this.props.container;

		let className = null;
		if(myContainer.unmanaged) {
			className = "warning";
		}
		if (this.props.isSelected) {
			className = "info";
		}

		const rowId = this.createRowId();
		const invisibleStyle = { height: this.state.invisibleHeight + "px" };

		if( this.state.isVisible ) {

			return this.renderVisible(myContainer);
		}

		else {
			return <tr id={rowId} key={myContainer.name} style={invisibleStyle} className={className} onClick={this.navigateToContainer}>
				{this.renderInvisible(myContainer)}
			</tr>;
		}
	},

	renderInvisible(container) {

		const invisibleStyle = { height: this.state.invisibleHeight + "px" };

		return ([
			<td key={"container_visibility_sensor_" + container.name}>
				<VisibilitySensor onChange={(isVisible) => this.handleVisibilityChange(isVisible)}/>
			</td>
			]);
	},

	renderVisible(container) {
		let actions = this.context.actions;
		let safetyArmed = this.safetyArmed = this.props.safetyArmed;
		let className = null;
		let unmanaged = container.unmanaged;
		let externalContainer = false;
		let startable = false;
		let stoppable = false;
		let purgeable = false;
		if(container.unmanaged) {
			className = "warning";
		}
		if (this.props.isSelected) {
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
				   onClick={(event) => this.props.navigateToContainer(container.name, event)}>
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
	}
}));
