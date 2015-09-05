import { connect } from 'react-redux';

import Tasklog from "./Tasklog.js";

export default connect(state => {
	return {task: state.activeTask}
})(React.createClass({
	getInitialState() {
		return {
			autoScroll: true
		}
	},

	render() {
		let task = this.props.task;
		if (!task) {
			return null;
		}
		return <div style={{height: "100%", display: "flex", flexDirection: "column"}}>
			<div style={{flex: "0 0 auto"}}>
				<h3>{task.title}<span className="text-muted pull-right">#{task.id}</span></h3>

				<div className="checkbox pull-right">
					<label>
						<input type="checkbox" checked={this.state.autoScroll}> Auto-Scroll</input>
					</label>
				</div>
			</div>
			<div style={{flex: "1 1 auto", overflow: "scroll"}}>
				<Tasklog key={task.id} taskId={task.id} autoScroll={this.state.autoScroll}/>
			</div>
		</div>;
	}
}));



