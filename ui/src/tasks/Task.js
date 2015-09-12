import { connect } from 'react-redux';

import Tasklog from "./Tasklog.js";

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
		if (nextProps.task.id !== this.props.task.id) {
			this.autoScrollTop = 0;
		}
	},

	scrollDown(override) {
		if (this.state.autoScroll || override) {
			let scrollElement = React.findDOMNode(this.refs.scrollContainer);
			scrollElement.scrollTop = scrollElement.scrollHeight;
			this.autoScrollTop = scrollElement.scrollTop;
		}
	},

	onScroll() {
		let scrollElement = React.findDOMNode(this.refs.scrollContainer);
		if (this.autoScrollTop !== scrollElement.scrollTop) {
			// Different from our set scrollTop, assume it was done by user and unset the autoscroll flag
			this.autoScrolltop = -1;
			this.setState({autoScroll: false});
		}
	},

	onChangeAutoscroll() {
		let autoScroll = React.findDOMNode(this.refs.autoScroll).checked;
		if (autoScroll) {
			this.scrollDown(true);
		}
		this.setState({autoScroll});
	},

	render() {
		let task = this.props.task;
		if (!task) {
			return null;
		}
		return <div style={{height: "100%", display: "flex", flexDirection: "column"}}>
			<div style={{flex: "0 0 auto", paddingRight: "20px"}}>
				<h3 style={{overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap"}}>{task.title}<span className="text-muted pull-right">#{task.id}</span></h3>

				<div className="checkbox pull-right">
					<label>
						<input ref="autoScroll" type="checkbox" checked={this.state.autoScroll}
							   onChange={this.onChangeAutoscroll}> Auto-Scroll</input>
					</label>
				</div>
			</div>
			<div ref="scrollContainer" style={{flex: "1 1 auto", overflow: "scroll"}} onScroll={this.onScroll}>
				<Tasklog key={task.id} taskId={task.id} scrollDown={this.scrollDown}/>
			</div>
		</div>;
	}
}));

