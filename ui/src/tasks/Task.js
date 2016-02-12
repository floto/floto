import { connect } from 'react-redux';

import Tasklog from "./Tasklog.js";
import {Navbar, Nav, NavItem, NavDropdown, CollapsibleNav, MenuItem, Button} from "react-bootstrap";
var Icon = require('react-fa');


export default connect(state => {
	return {task: state.activeTask};
})(React.createClass({
	getInitialState() {
		this.scrollDown = _.debounce(this.scrollDown, 10, {maxWait: 10, leading: true});
		this.autoScrollTop = 0;
		return {
			autoScroll: true
		};
	},

	componentWillUpdate(nextProps) {
		if (!this.props.task) {
			return;
		}
		if (nextProps.task && nextProps.task.id !== this.props.task.id) {
			this.autoScrollTop = 0;
		}
	},

	scrollDown(override) {
		if (this.state.autoScroll || override) {
			let scrollElement = ReactDOM.findDOMNode(this.refs.scrollContainer);
			scrollElement.scrollTop = scrollElement.scrollHeight;
			this.autoScrollTop = scrollElement.scrollTop;
		}
	},

	onScroll() {
		let scrollElement = ReactDOM.findDOMNode(this.refs.scrollContainer);
		if (this.autoScrollTop !== scrollElement.scrollTop) {
			// Different from our set scrollTop, assume it was done by user and unset the autoscroll flag
			this.autoScrolltop = -1;
			this.setState({autoScroll: false});
		}
	},

	onChangeAutoscroll() {
		let autoScroll = ReactDOM.findDOMNode(this.refs.autoScroll).checked;
		if (autoScroll) {
			this.scrollDown(true);
		}
		this.setState({autoScroll});
	},

	onDownload() {
		let task = this.props.task;
		let tasklogNode = ReactDOM.findDOMNode(this);
		if (tasklogNode) {
			let escapedTitle = task.title;
			escapedTitle = escapedTitle.replace(/&/g, '&amp;')
				.replace(/>/g, '&gt;')
				.replace(/</g, '&lt;')
				.replace(/"/g, '&quot;');
			var elementHtml = `<!DOCTYPE html><html><head><meta charset="utf-8" /><title>${escapedTitle}</title><style type="text/css">
			input, button, label  {
				display: none;
			}
			.pull-right {
				    float: right !important;
			}
			table {
				border-spacing: 0;
				border-collapse: collapse;
			}
			.table-bordered > tbody > tr > td {
    			border: 1px solid #ddd;
    			padding: 4px;
			}
			.table-striped > tbody > tr:nth-of-type(odd) {
 			   background-color: #f9f9f9;
			}
		</style></head><body>` + tasklogNode.innerHTML + "</body></html>";
			var link = document.createElement('a');
			let mimeType = mimeType || 'text/plain';
			let filename = "task-" + task.id + "-" + task.title.replace(/[^a-zA-Z0-9_ ]/gi, '_').toLowerCase() + ".html";

			link.setAttribute('download', filename);
			link.setAttribute('href', 'data:' + mimeType + ';charset=utf-8,' + encodeURIComponent(elementHtml));
			link.click();
		}
	},

	render() {
		let task = this.props.task;
		if (!task) {
			return null;
		}
		return <div style={{height: "100%", display: "flex", flexDirection: "column"}}>
			<div style={{flex: "0 0 auto", paddingRight: "20px"}}>
				<h3 style={{overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap"}}>{task.title}<span
					className="text-muted pull-right">#{task.id}</span></h3>
				<Button bsStyle="default" onClick={this.onDownload}
				><Icon name="download"/> Download</Button>

				<div className="checkbox pull-right">
					<label>
						<input ref="autoScroll" type="checkbox" checked={this.state.autoScroll}
							   onChange={this.onChangeAutoscroll}/> Auto-Scroll
					</label>
				</div>
			</div>
			<div ref="scrollContainer" style={{flex: "1 1 auto", overflow: "scroll"}} onScroll={this.onScroll}>
				<Tasklog ref="tasklog" key={task.id} taskId={task.id} scrollDown={this.scrollDown}/>
			</div>
		</div>;
	}
}));

