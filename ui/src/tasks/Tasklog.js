import taskService from "./taskService.js";


var classMap = {
	ERROR: "danger",
	WARN: "warning"
};


export default React.createClass({

	componentDidMount() {
		this.scrollDown = _.debounce(this.scrollDown, 10, {maxWait: 10, leading: true});
		var table = $(React.findDOMNode(this.refs.table));

		taskService.subscribeToLog(this.props.taskId, (entry) => {
			var cls = classMap[entry.level];
			var classPart = "";
			if (cls) {
				classPart = ' class="' + cls + '" '
			}
			var stackTracePart = "";
			if (entry.stackTrace) {
				stackTracePart = "<pre>" + entry.stackTrace + "</pre>";
			}
			var row = "<tr" + classPart + ">";
			row += "<td>" + entry.timestamp.substring(11, 23) + "</td>";
			row += "<td>" + entry.message + stackTracePart + "</td>";
			row += "<td>" + entry.level + "</td>";
			row += "<td>" + entry.logger + "</td>";
			row += "</tr>";
			table.append(row);
			this.scrollDown();
		});
	},

	scrollDown() {
//			if(scope.flotoAutoScroll) {
		let scrollElement = React.findDOMNode(this).parentElement;
		scrollElement.scrollTop = scrollElement.scrollHeight;
//				autoScrolltop = scrollElement.scrollTop;
//			}
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
