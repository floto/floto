import { connect } from 'react-redux';

import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";


var Icon = require('react-fa')

import TimeAgo from "react-timeago";

import moment from "moment";

import * as actions from "../actions/actions.js";

const iconMap = {
	ERROR: "exclamation-circle",
	SUCCESS: "check",
	RUNNING: "spinner",
	QUEUED: "clock-o",
	CANCELLED: "remove"
};

const classMap = {
	SUCCESS: "success",
	ERROR: "danger"
};
export default connect(state => {
	return {tasks: state.tasks}
})(React.createClass({

			componentDidMount() {
				this.refreshTasks();
			},

			refreshTasks() {
				actions.loadTasks(this.props.dispatch);
			},

			renderTask(task) {
				let icon = iconMap[task.status] || "question";
				let spin = task.status === "RUNNING";
				let className = classMap[task.status];
				return <tr key={task.id} className={className}>
					<td><span className="text-muted">#{task.id}</span></td>
					<td><Icon spin={spin} name={icon} /> {task.title}</td>
					<td title={task.creationDate}><TimeAgo date={task.creationDate} /></td>
					<td title={task.endDate}><TimeAgo date={task.endDate} /></td>
					<td>{moment.duration(task.durationInMs, "ms").humanize()}</td>
				</tr>;
			},

			render() {
				let tasks = this.props.tasks || [];
				return <div style={{padding: "0px 20px"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap"}}>
						<div style={{flex: 1}}>
							<h2>Tasks</h2>
							<Table bordered striped hover condensed>
								<thead>
								<tr>
									<th>ID</th>
									<th>Title</th>
									<th>Started</th>
									<th>Completed</th>
									<th>Duration</th>
								</tr>
								</thead>
								<tbody>
								{tasks.map(this.renderTask)}
								</tbody>
							</Table>
						</div>
						<div style={{flex: 1, paddingLeft: 20}}>
							<h3>nginx</h3>
						</div>
					</div>
				</div>;

			}
		}
	)
);
