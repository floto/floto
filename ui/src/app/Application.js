import NavigationBar from "./NavigationBar.js"
import Containers from "../containers/Containers"

import { Redirect, Router, Route } from 'react-router'


export default React.createClass({
	render() {
		let containers = [{name: "foox"}];
		return <div style={{height: "100%"}}>
			<NavigationBar />
			<div style={{position: "absolute", top: 50, bottom: 0, width: "100%"}}>
			{this.props.children}
				</div>
		</div>;
	}
});

// 			<Containers containers={containers} />
