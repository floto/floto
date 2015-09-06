import {Navbar, Nav, NavItem, NavDropdown, MenuItem, Button} from "react-bootstrap";
import {NavItemLink} from 'react-router-bootstrap';
import { connect } from 'react-redux';

var Icon = require('react-fa');

import * as actions from "../actions/actions.js";

import Switch from "../components/Switch.js";



export default connect(state => {
	return {serverState: state.serverState, clientState: state.clientState, site: state.manifest.site}
})(React.createClass({

	getInitialState() {
		return {}
	},

	recompileManifest() {
		actions.recompileManifest(this.props.dispatch);
	},

	onChangeSafety(safetyArmed) {
		actions.changeSafety(this.props.dispatch, safetyArmed);
	},


	render() {
		let isCompiling = this.props.serverState.isCompiling;
		let site = this.props.site ||  {projectName: "?", projectRevision: "?"};
		let siteName = site.projectName || site.domainName;
		return <Navbar fixedTop fluid brand={<a href="#"><span><img src="/img/floto-icon.svg" style={{height: 24}} /></span>&nbsp;floto</a>}>
			<Nav>
				<NavItemLink to="containers"><Icon name="cubes" />&nbsp;&nbsp;Containers</NavItemLink>
				<NavItemLink to="hosts"><Icon name="server" />&nbsp;&nbsp;Hosts</NavItemLink>
				<NavItemLink to="tasks"><Icon name="list" />&nbsp;&nbsp;Tasks</NavItemLink>
				<NavItemLink to="tasks"><Icon name="file-archive-o" />&nbsp;&nbsp;Patches</NavItemLink>
				<NavDropdown eventKey={4} title={<span><Icon name="download" />&nbsp;&nbsp;Export</span>} id='basic-nav-dropdown'>
					<li><a href="api/export/container-logs">Container Logs</a></li>
					<li><a href="api/manifest" download={`manifest-${siteName}-${site.projectRevision}.json`}>Manifest</a></li>
				</NavDropdown>
				<NavItemLink to="manifest"><Icon name="file-text-o" />&nbsp;&nbsp;Manifest</NavItemLink>
				<form className="navbar-form navbar-left">
					<div className="form-group">
						<Button disabled={isCompiling} bsStyle='primary' bsSize='small' onClick={this.recompileManifest} style={{width: "10em", textAlign: "left"}}><Icon spin={isCompiling} name="cog" />&nbsp;&nbsp;{isCompiling ? "Recompiling..." : "Recompile"}</Button>
						&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
						<Switch checked={this.props.clientState.safetyArmed} onChange={this.onChangeSafety}/>
					</div>
				</form>
				<span className="navbar-brand pull-right" href="#">
				</span>
			</Nav>
			<div className="nav navbar-nav navbar-right" style={{textAlign: "center", paddingTop: "10px", paddingRight: "20px", height: "20px"}}>
				<span style={{color: site.siteColor}}>{siteName}{site.environment?<span ng-if="site.environment"> ({site.environment})</span>:null}</span><br />
				<span style={{fontSize: "80%", position: "relative", top: "-4px"}} className="text-muted">{site.projectRevision}</span>
				</div>
		</Navbar>;
	}
}));

