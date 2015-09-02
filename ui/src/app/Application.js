import NavigationBar from "./NavigationBar.js"
import Containers from "../containers/Containers"

import { Redirect, Router, Route } from 'react-router'


export default React.createClass({
	render() {
		let containers = [{name: "foox"}];
		return <div>
			<NavigationBar />
			{this.props.children}
		</div>;
	}
});

// 			<Containers containers={containers} />
