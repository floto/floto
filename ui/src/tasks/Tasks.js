import { connect } from 'react-redux';

import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";

var Icon = require('react-fa');

import TimeAgo from "react-timeago";

import moment from "moment";

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
	return {tasks: state.tasks, activeTask: state.activeTask};
})(React.createClass({
			contextTypes: {
				actions: React.PropTypes.object.isRequired,
				router: React.PropTypes.object.isRequired
			},

			componentDidMount() {
				this.refreshTasks();
			},


			refreshTasks() {
				this.context.actions.loadTasks();
			},


			renderTask(task) {
				let icon = iconMap[task.status] || "question";
				let spin = task.status === "RUNNING";
				let className = classMap[task.status];
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
			},

			render() {
				let tasks = this.props.tasks || [];
				return <div style={{height: "100%"}}>
					<div style={{height: "100%", display: "flex", flexDirection: "row", flexWrap: "nowrap"}}>
						<div style={{height: "100%", flex: "1 1"}}>
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

						<div style={{flex: "1", paddingLeft: 20, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);


