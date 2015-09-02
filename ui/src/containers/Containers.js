import { connect } from 'react-redux';

export default connect(state => { return {containers: state.manifest.containers}})(React.createClass({

	render() {
		let containers = this.props.containers || [];
		return <div>
			<h2>Containers</h2>
			{containers.map((container) => <div key={container.name}>{container.name}</div>)}
		</div>;
	}

}));
