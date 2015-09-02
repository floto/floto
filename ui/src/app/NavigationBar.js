import {Navbar, Nav, NavItem, NavDropdown, MenuItem, Button} from "react-bootstrap";

export default React.createClass({
	render() {
		return <Navbar fluid brand={<a href="#"><span><img src="/img/floto-icon.svg" style={{height: 24}} /></span>&nbsp;floto</a>}>
			<Nav>
				<NavItem eventKey={1} href='#'>Containers</NavItem>
				<NavItem eventKey={2} href='#'>Hosts</NavItem>
				<NavItem eventKey={3} href='#'>Tasks</NavItem>
				<NavDropdown eventKey={4} title='Export' id='basic-nav-dropdown'>
					<MenuItem eventKey='1'>Container Logs</MenuItem>
					<MenuItem eventKey='2'>Manifest</MenuItem>
				</NavDropdown>
				<NavItem eventKey={5} href='#'>Manifest</NavItem>
				<form className="navbar-form navbar-left">
					<Button bsStyle='primary' bsSize='small'>Recompile</Button>
				</form>
			</Nav>
		</Navbar>;
	}
});
