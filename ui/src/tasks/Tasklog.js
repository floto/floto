import { connect } from 'react-redux';

export default connect(state => {
	return {task: state.activeTask}
})(React.createClass({
	render() {
		let task = this.props.task || {};
		return <div>
			<h3>{task.title}<span className="text-muted pull-right">#{task.id}</span></h3>

			<div classname="checkbox pull-right">
				<label>
					<input type="checkbox"> Auto-Scroll</input>
				</label>
			</div>
		</div>;
	}
}));
