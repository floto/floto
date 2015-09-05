import NavigationBar from "./NavigationBar.js"
import Containers from "../containers/Containers"

import { Redirect, Router, Route } from 'react-router'


export default React.createClass({
	render() {
		return <div style={{height: "100vh"}}>
			<NavigationBar />
			<div style={{position: "absolute", top: 40, height: "calc(100vh - 60px)", left: 20, width: "calc(100vw - 40px)"}}>
			{this.props.children}
				</div>
		</div>;
	}
});

