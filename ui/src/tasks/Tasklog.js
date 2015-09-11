import moment from "moment";

import taskService from "./taskService.js";


var classMap = {
	ERROR: "danger",
	WARN: "warning"
};


export default React.createClass({

	componentDidMount() {
		var table = $(React.findDOMNode(this.refs.table));

		taskService.subscribeToLog(this.props.taskId, (entry) => {
			if(!this.isMounted()) {
				// Not mounted anymore, bail early
				// TODO: unsubscribe on unmount
				return;
			}
			var cls = classMap[entry.level];
			var classPart = "";
			if (cls) {
				classPart = ' class="' + cls + '" '
			}
			var stackTracePart = "";
			if (entry.stackTrace) {
				stackTracePart = "<pre>" + entry.stackTrace + "</pre>";
			}
			let timestamp = moment(entry.timestamp).format('HH:mm:ss.SSS');
			var row = "<tr" + classPart + ">";
			row += "<td>" + timestamp + "</td>";
			row += "<td>" + entry.message + stackTracePart + "</td>";
			row += "<td>" + entry.level + "</td>";
			row += "<td>" + entry.logger + "</td>";
			row += "</tr>";
			table.append(row);
			this.props.scrollDown();
		});
	},

	render() {
		return <div style={{width: "100%", height: "100%"}}>
			<table ref="table" className="table table-bordered table-striped table-hover table-condensed">
				<col style={{width: "5em"}}/>
				<col />
				<col style={{width: "5em"}}/>
				<col style={{width: "5em"}}/>
			</table>
		</div>;
	}
});
