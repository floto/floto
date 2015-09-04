import {Navbar, Nav, NavItem, NavDropdown, MenuItem, Button} from "react-bootstrap";
import {NavItemLink} from 'react-router-bootstrap';
import { connect } from 'react-redux';

import * as actions from "../actions/actions.js";

var Icon = require('react-fa');


export default connect(state => {
	return {containers: state.manifest.containers}
})(React.createClass({

	recompileManifest() {
		actions.recompileManifest(this.props.dispatch);
	},


	render() {
		return <Navbar fixedTop fluid brand={<a href="#"><span><img src="/img/floto-icon.svg" style={{height: 24}} /></span>&nbsp;floto</a>}>
			<Nav>
				<NavItemLink to="containers"><Icon name="cubes" /> Containers</NavItemLink>
				<NavItemLink to="hosts"><Icon name="server" /> Hosts</NavItemLink>
				<NavItemLink to="tasks"><Icon name="list" /> Tasks</NavItemLink>
				<NavItemLink to="tasks"><Icon name="file-archive-o" /> Patches</NavItemLink>
				<NavDropdown eventKey={4} title={<span><Icon name="download" /> Export</span>} id='basic-nav-dropdown'>
					<MenuItem eventKey='1'>Container Logs</MenuItem>
					<MenuItem eventKey='2'>Manifest</MenuItem>
				</NavDropdown>
				<NavItem eventKey={5} href='#'><Icon name="file-text-o" /> Manifest</NavItem>
				<form className="navbar-form navbar-left">
					<Button bsStyle='primary' bsSize='small' onClick={this.recompileManifest}><Icon name="cog" /> Recompile</Button>
				</form>
			</Nav>
		</Navbar>;
	}
}));
