import { connect } from 'react-redux';

import { Redirect, Router, Route } from 'react-router';

import NavigationBar from "./NavigationBar.js";
import Containers from "../containers/Containers";



export default connect(state => {
	return {flotoInfo: state.flotoInfo};
})(React.createClass({
	render() {
		return <div style={{height: "100vh"}}>
			<NavigationBar />

			<div
				style={{position: "absolute", top: 40, height: "calc(100vh - 60px)", left: 20, width: "calc(100vw - 20px)"}}>
				{this.props.children}
			</div>
			<div style={{position: "fixed", bottom: "0px", right: 10, fontSize: "80%"}}
				 className="text-muted">{this.props.flotoInfo.flotoRevision}</div>
		</div>;
	}
}));



