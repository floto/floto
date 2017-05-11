import {Table, Button, ButtonGroup} from "react-bootstrap";
import { connect } from 'react-redux';

import RedeployButton from "../components/RedeployButton.js";
import ContainerRow from "./ContainerRow.js"

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
	propTypes: {
		isLazyRenderingActive: React.PropTypes.bool
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

	renderContainers(containers) {

		var rows = [];

		console.log("containers: " + containers.length);

		let debugContainerCount = Math.min( 100, containers.length );
		debugContainerCount = containers.length;
		for( let index = 0; index < debugContainerCount; index++) {

			const container = containers[ index ];
			const isSelected = this.props.selectedContainer === container;

			rows.push( <ContainerRow key={"key_" + container.name}
									 isSelected={isSelected}
									 container={container}
									 location={this.props.location}
									 navigateToContainer={this.navigateToContainer}
									 safetyArmed={this.safetyArmed}/> );
		}

		return rows;
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
				{this.renderContainers(containers)}
				</tbody>
			</Table>
		</div>;
	},

	componentDidMount() {

		if( this.props.isLazyRenderingActive ) {
			this.props.invokeNextGroupRendering();
		}
	}
}));

