import NavigationBar from "./NavigationBar.js"
import Containers from "../containers/Containers"

export default React.createClass({
	render() {
		let containers = [{name: "foo"}];
		return <div>
			<NavigationBar />
			<Containers containers={containers} />
		</div>;
	}
});
