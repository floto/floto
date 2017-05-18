import { connect } from 'react-redux';
import JsonInspector from 'react-json-inspector';
import React from 'react';

require("react-json-inspector/json-inspector.css");

class Manifest extends React.Component {

	render() {
		let manifest = this.props.manifest || [];
		return <div
			style={{height: "100%", width: "100%", display: "flex", flexDirection: "column", flexWrap: "nowrap"}}>
			<div style={{flex: "0 0 auto"}}>
				<h2>Manifest</h2>
			</div>
			<div style={{flex: "1 1 auto", overflowY: "scroll", overflowX: "auto"}}>
				<div style={{width: "calc(100% - 50px)", height: 30}}>
					<JsonInspector data={ manifest }/>
				</div>
			</div>
		</div>;
	}
}

export default connect(state => {
	return {
		manifest: state.manifest
	};
})(Manifest);





