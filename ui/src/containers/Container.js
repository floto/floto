import { connect } from 'react-redux';

export default connect(state => {
	return {container: state.selectedContainer, templateMap: state.templateMap}
})(React.createClass({
	render() {
		let container = this.props.container;
		if(!container) {
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
				file: encodeURIComponent("template/"+template.destination),
				destination: template.destination
			});
		});
		return <div>
			<h3>{container.name}</h3>
			<ul className="nav nav-pills nav-stacked" role="tablist">
				<li key="logtail"><a title="Log (tail)" ui-sref="container.log()">Logtail</a></li>
				{fileTargets.map((fileTarget) => <li key={fileTarget.file}><a title={fileTarget.destination} ui-sref="container.file({file: fileTarget.file})">{fileTarget.name}</a></li>)}
			</ul>
		</div>;
	}
}));


