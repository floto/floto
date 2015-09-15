require('expose?jQuery!expose?$!jquery');
require("script!bootstrap-switch");
require("bootstrap-switch/dist/css/bootstrap3/bootstrap-switch.css");


export default React.createClass({
	componentDidMount() {
		var domNode = React.findDOMNode(this);
		$(domNode).bootstrapSwitch({
			onText: "Armed",
			offText: "Safe",
			onColor: "danger",
			labelText: "Safety",
			size: "normal",
			onSwitchChange: (event, state) => {
				this.props.onChange(state);
			}
		});
	},

	onChange() {
		// handled by bootstrapSwitch;
	},

	componentDidUpdate() {
		var domNode = React.findDOMNode(this);
		$(domNode).bootstrapSwitch('state', this.props.checked, true);
	},

	render() {
		return <input type="checkbox" name="my-checkbox" checked onChange={this.onChange}/>;
	}
});



