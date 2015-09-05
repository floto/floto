import {Navbar, Nav, NavItem, NavDropdown, MenuItem, Button} from "react-bootstrap";
import {NavItemLink} from 'react-router-bootstrap';
import { connect } from 'react-redux';

var Icon = require('react-fa');

import * as actions from "../actions/actions.js";

import Switch from "../components/Switch.js";



export default connect(state => {
	return {serverState: state.serverState}
})(React.createClass({

	getInitialState() {
		return {}
	},

	recompileManifest() {
		actions.recompileManifest(this.props.dispatch);
	},

	onChangeSafety(newState) {
	},


	render() {
		let isCompiling = this.props.serverState.isCompiling;
		return <Navbar fixedTop fluid brand={<a href="#"><span><img src="/img/floto-icon.svg" style={{height: 24}} /></span>&nbsp;floto</a>}>
			<Nav>
				<NavItemLink to="containers"><Icon name="cubes" />&nbsp;&nbsp;Containers</NavItemLink>
				<NavItemLink to="hosts"><Icon name="server" />&nbsp;&nbsp;Hosts</NavItemLink>
				<NavItemLink to="tasks"><Icon name="list" />&nbsp;&nbsp;Tasks</NavItemLink>
				<NavItemLink to="tasks"><Icon name="file-archive-o" />&nbsp;&nbsp;Patches</NavItemLink>
				<NavDropdown eventKey={4} title={<span><Icon name="download" />&nbsp;&nbsp;Export</span>} id='basic-nav-dropdown'>
					<MenuItem eventKey='1'>Container Logs</MenuItem>
					<MenuItem eventKey='2'>Manifest</MenuItem>
				</NavDropdown>
				<NavItemLink to="manifest"><Icon name="file-text-o" />&nbsp;&nbsp;Manifest</NavItemLink>
				<form className="navbar-form navbar-left">
					<div className="form-group">
						<Button disabled={isCompiling} bsStyle='primary' bsSize='small' onClick={this.recompileManifest} style={{width: "10em", textAlign: "left"}}><Icon spin={isCompiling} name="cog" />&nbsp;&nbsp;{isCompiling ? "Recompiling..." : "Recompile"}</Button>
						&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
						<Switch checked={this.state.safetyState} onChange={this.onChangeSafety}/>
					</div>
				</form>
			</Nav>
		</Navbar>;
	}
}));
