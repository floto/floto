import { connect } from 'react-redux';

var JsonInspector = require('react-json-inspector');

require("react-json-inspector/json-inspector.css");

export default connect(state => {
	return {manifest: state.manifest};
})(React.createClass({

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
	)
);





