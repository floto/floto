import moment from "moment";

import taskService from "./taskService.js";
import websocketService from "../util/websocketService.js";


var classMap = {
	ERROR: "danger",
	WARN: "warning"
};


export default React.createClass({

	componentDidMount() {
		var table = $(ReactDOM.findDOMNode(this.refs.table));
		this.streamId = taskService.subscribeToLog(this.props.taskId, (entry) => {
			if (!this.isMounted()) {
				// Not mounted anymore, bail early
				return;
			}
			var cls = classMap[entry.level];
			var classPart = "";
			if (cls) {
				classPart = ' class="' + cls + '" ';
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

	componentWillUnmount() {
		websocketService.sendMessage({
			type: "unsubscribeFromTaskLog",
			streamId: this.streamId
		});
	},

	render() {
		return <div style={{width: "100%", height: "100%"}}>
			<table className="table table-bordered table-striped table-hover table-condensed">
				<colgroup>
					<col style={{width: "6em"}}/>
					<col />
					<col style={{width: "5em"}}/>
					<col style={{width: "5em"}}/>
				</colgroup>
				<tbody ref="table" ></tbody>
			</table>
		</div>;
	}
});
