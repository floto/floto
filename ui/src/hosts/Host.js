import { connect } from 'react-redux';
import {Link} from "react-router";

export default connect(state => {
	return {
		host: state.selectedHost,
		templateMap: state.templateMap,
		selectedFile: state.selectedFile,
		selectedFileError: state.selectedFileError
	};
})(React.createClass({
	render() {
		let host = this.props.host;
		if (!host) {
			return null;
		}
		let fileTargets = [];
		let externalVm = (host.externalVm !== null && host.externalVm === true) ? true : false;
		if(!externalVm){
			fileTargets = [
				{name: "PostDeploy", file: "script%2FpostDeploy"},
				{name: "Reconfigure", file: "script%2Freconfigure"}
			];
			let templates = this.props.templateMap[`host:${host.name}`];
			_.forEach(templates, (template) => {
				fileTargets.push({
					name: template.name,
					file: encodeURIComponent("template/" + template.destination),
					destination: template.destination
				});
			});
		}

		let selectedFileName = this.props.selectedFile && this.props.selectedFile.fileName || this.props.selectedFileError && this.props.selectedFileError.fileName;
		if(selectedFileName) {
			// normalize filename
			selectedFileName = encodeURIComponent(decodeURIComponent(decodeURIComponent(selectedFileName)));
		}
		return <div style={{height: "100%", display: "flex", flexDirection: "column"}}>
			<div style={{flex: "0 0 auto", paddingRight: "20px"}}>
				<h3>{host.name}</h3>
			</div>
			<div style={{flex: "1 1 auto", display: "flex", flexDirection: "row", minHeight: "0px"}}>
				<div style={{flex: "0 0 auto", overflow: "scroll", minHeight: "0px", width: "10em"}}>
					<ul className="nav nav-pills nav-stacked" role="tablist">
						{fileTargets.map((fileTarget) => {
							let className = null;
							if (selectedFileName === fileTarget.file) {
								className = "active";
							}
							return <li key={fileTarget.file} className={className}>
								<Link to={{pathname:`/hosts/${host.name}/file/${fileTarget.file}`,
									  query: this.props.location.query}}
									  title={fileTarget.destination}
									>{fileTarget.name}</Link>
							</li>;
						})}
					</ul>
				</div>
				{!externalVm && 
					<div key={selectedFileName} style={{flex: "1 1 auto", minHeight: "0px", height: "calc(100vh - 116px)", overflow: "hidden"}}>
					{this.props.children}
					</div>
				}
			</div>

		</div>;

	}
}));



