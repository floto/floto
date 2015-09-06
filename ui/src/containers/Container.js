import { connect } from 'react-redux';
import {Link} from "react-router";

export default connect(state => {
	return {container: state.selectedContainer, templateMap: state.templateMap}
})(React.createClass({
	render() {
		let container = this.props.container;
		if (!container) {
			return null;
		}
		let fileTargets = [
			{name: "Logfile", file: "log"},
			{name: "Buildlog", file: "buildlog"},
			{name: "Image", file: "dockerfile%2Fimage"},
			{name: "Container", file: "dockerfile%2Fcontainer"}
		];
		let templates = this.props.templateMap[`container:${container.name}`];
		_.forEach(templates, (template) => {
			fileTargets.push({
				name: template.name,
				file: encodeURIComponent(encodeURIComponent(encodeURIComponent("template/" + template.destination))),
				destination: template.destination
			});
		});

		return <div style={{height: "100%", display: "flex", flexDirection: "column"}}>
			<div style={{flex: "0 0 auto", paddingRight: "20px"}}>
				<h3>{container.name}</h3>

			</div>
			<div style={{flex: "1 1 auto", display: "flex", flexDirection: "row", minHeight: "0px"}}>
				<div style={{flex: "0 1 auto", overflow: "scroll", minHeight: "0px", minWidth: "10em"}}>
					<ul className="nav nav-pills nav-stacked" role="tablist">
						<li key="logtail"><a title="Log (tail)" ui-sref="container.log()">Logtail</a></li>
						{fileTargets.map((fileTarget) => <li key={fileTarget.file}>
							<Link to={`/containers/${container.name}/file/${fileTarget.file}`}
								  title={fileTarget.destination}
								>{fileTarget.name}</Link>
						</li>)}
					</ul>
				</div>
				<div style={{flex: "1 1 auto", minHeight: "0px", overflow: "scroll"}}>
					{this.props.children}
				</div>
			</div>

		</div>;






	}
}));





