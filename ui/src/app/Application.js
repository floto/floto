import { connect } from 'react-redux';
import NavigationBar from "./NavigationBar.js";
import React from 'react';

class Application extends React.Component {

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
}

export default connect(state => {
	return {flotoInfo: state.flotoInfo};
})(Application);



