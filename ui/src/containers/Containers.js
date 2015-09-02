export default React.createClass({

	render() {
		let containers = this.props.containers;
		return <div>
			{containers.map((container) => <div>{container.name}</div>)}
		</div>;
	}

});
