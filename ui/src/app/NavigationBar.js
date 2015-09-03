import {Navbar, Nav, NavItem, NavDropdown, MenuItem, Button} from "react-bootstrap";
import {NavItemLink} from 'react-router-bootstrap';
import { connect } from 'react-redux';

import * as actions from "../actions/actions.js";

export default connect(state => {
	return {containers: state.manifest.containers}
})(React.createClass({

	recompileManifest() {
		actions.recompileManifest(this.props.dispatch);
	},

	render() {
		return <Navbar fluid brand={<a href="#"><span><img src="/img/floto-icon.svg" style={{height: 24}} /></span>&nbsp;floto</a>}>
			<Nav>
				<NavItemLink to="containers">Containers</NavItemLink>
				<NavItemLink to="hosts">Hosts</NavItemLink>
				<NavItem eventKey={3} href='#'>Tasks</NavItem>
				<NavDropdown eventKey={4} title='Export' id='basic-nav-dropdown'>
					<MenuItem eventKey='1'>Container Logs</MenuItem>
					<MenuItem eventKey='2'>Manifest</MenuItem>
				</NavDropdown>
				<NavItem eventKey={5} href='#'>Manifest</NavItem>
				<form className="navbar-form navbar-left">
					<Button bsStyle='primary' bsSize='small' onClick={this.recompileManifest}>Recompile</Button>
				</form>
			</Nav>
		</Navbar>;
	}
}));
