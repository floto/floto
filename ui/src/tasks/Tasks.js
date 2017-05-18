import { connect } from 'react-redux';
import {Table, Button} from "react-bootstrap";
import Icon from 'react-fa';
import TimeAgo from "react-timeago";
import moment from "moment";
import React from 'react';

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

class Tasks extends React.Component {

	constructor() {
		super();

		this.refreshTasks = this.refreshTasks.bind(this);
		this.renderTask = this.renderTask.bind(this);
	}

	componentDidMount() {
		this.refreshTasks();
	}

	refreshTasks() {
		this.context.actions.loadTasks();
	}

	renderTask(task) {
		let icon = iconMap[task.status] || "question";
		let spin = task.status === "RUNNING";
		let className = classMap[task.status];
		if(task.status == "SUCCESS" && task.numberOfWarnings) {
			icon = "exclamation-triangle";
			className = "warning";
		}
		let style = null;
		if (task === this.props.activeTask) {
			className = "info";
		}
		return <tr key={task.id} className={className}
				   onClick={() => this.context.router.push('/tasks/'+task.id)}>
			<td><span className="text-muted">#{task.id}</span></td>
			<td><Icon spin={spin} name={icon}/> {task.title}</td>
			<td title={task.creationDate}>{task.creationDate ? <TimeAgo date={task.creationDate}/> : ""}</td>
			<td title={task.endDate}>{task.endDate ? <TimeAgo date={task.endDate}/> : ""}</td>
			<td>{moment.duration(task.durationInMs, "ms").humanize()}</td>
		</tr>;
	}

	render() {
		let tasks = this.props.tasks || [];
		return <div style={{height: "100%"}}>
			<div style={{height: "100%", display: "flex", flexDirection: "row", flexWrap: "nowrap"}}>
				<div style={{height: "100%", flex: "1 1 auto", width: "50%"}}>
					<div style={{height: "100%", display: "flex", flexDirection: "column", flexWrap: "nowrap"}}>
						<div style={{flex: "0 0 auto"}}>
							<h2>Tasks<Button className="pull-right" bsStyle="default"
											 onClick={this.refreshTasks}>Refresh</Button>
							</h2>
						</div>
						<div style={{flex: "1 1 auto", overflowY: "scroll", cursor: "pointer"}}>
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
					</div>
				</div>

				<div style={{flex: "1 1 auto", paddingLeft: 20, height: "100%", width: "50%"}}>
					{this.props.children}
				</div>
			</div>
		</div>;

	}
}

Tasks.contextTypes = {
	actions: React.PropTypes.object.isRequired,
	router: React.PropTypes.object.isRequired
}

export default connect(state => {
	return {
		tasks: state.tasks,
		activeTask: state.activeTask
	};
})(Tasks);


