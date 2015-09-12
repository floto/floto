import { connect } from 'react-redux';
import {Link} from "react-router";

export default connect(state => {
	return {
		container: state.selectedContainer,
		templateMap: state.templateMap,
		selectedFile: state.selectedFile,
		selectedFileError: state.selectedFileError
	};
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

		let selectedFileName = this.props.selectedFile && this.props.selectedFile.fileName || this.props.selectedFileError && this.props.selectedFileError.fileName;

		return <div style={{height: "100%", display: "flex", flexDirection: "column"}}>
			<div style={{flex: "0 0 auto", paddingRight: "20px"}}>
				<h3>{container.name}</h3>

			</div>
			<div style={{flex: "1 1 auto", display: "flex", flexDirection: "row", minHeight: "0px"}}>
				<div style={{flex: "0 0 auto", overflow: "scroll", minHeight: "0px", width: "10em"}}>
					<ul className="nav nav-pills nav-stacked" role="tablist">
						<li key="logtail"><a title="Log (tail)" ui-sref="container.log()">Logtail</a></li>
						{fileTargets.map((fileTarget) => {
							let className = null;
							if (selectedFileName === fileTarget.file) {
								className = "active";
							}
							return <li key={fileTarget.file} className={className}>
								<Link to={`/containers/${container.name}/file/${fileTarget.file}`}
									  query={this.props.location.query}
									  title={fileTarget.destination}
									>{fileTarget.name}</Link>
							</li>;
						})}
					</ul>
				</div>
				<div key={selectedFileName} style={{flex: "1 1 auto", minHeight: "0px", overflow: "scroll"}}>
					{this.props.children}
				</div>
			</div>

		</div>;


	}
}));





