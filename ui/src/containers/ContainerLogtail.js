var VirtualList = require('react-virtual-list');

import { connect } from 'react-redux';

import { Navigation } from 'react-router';

import websocketService from "../util/websocketService.js";

let handlers = {};

websocketService.addMessageHandler("containerLogMessages", function (message) {
	var handler = handlers[message.streamId];
	if (handler) {
		handler(message);
	}
});

let MessageLine = React.createClass({
	shouldComponentUpdate(nextProps, nextState) {
		return false;
	},

	render() {
		let message = this.props.message;
		return <div className={message.className}><span
			className="log-timestamp">{message.timestamp}  </span>{message.log}</div>;
	}
});

let nextId = 0;
const maximumMessageLength = 1000;

export default connect(state => {
	return {selectedFile: state.selectedFile, selectedFileError: state.selectedFileError};
})(React.createClass({
	mixins: [Navigation],

	getInitialState() {
		return {
			messages: [],
			autoScroll: true,
			showTimestamps: true
		};
	},

	componentDidMount() {
		let containerName = this.props.params.containerName;
		var myStreamId = this.streamId = +(new Date()) + "-" + Math.random();
		handlers[myStreamId] = (data) => {
			data.messages.forEach((message) => {
				var className = "log-" + message.stream;
				var log = _.escape(message.log);
				var timestamp = message.time.substr(0, 10) + " " + message.time.substr(11, 8);
				this.state.messages.push({key: nextId, className, log, timestamp});
				nextId++;
			});
			if(this.state.messages.length > maximumMessageLength) {
				let oversize = this.state.messages.length - maximumMessageLength;
				this.state.messages.splice(0, oversize);
			}
			this.forceUpdate();
		};
		websocketService.sendMessage({
			type: "subscribeToContainerLog",
			streamId: myStreamId,
			containerName: containerName
		});
		this.setState({container: ReactDOM.findDOMNode(this.refs.container)});
	},

	componentDidUpdate() {
		if(this.state.autoScroll) {
			let container = ReactDOM.findDOMNode(this.refs.container);
			container.scrollTop = container.scrollHeight;
			this.autoScrollTop = container.scrollTop;
		}
	},

	componentWillUnmount() {
		websocketService.sendMessage({
			type: "unsubscribeFromContainerLog",
			streamId: this.streamId
		});
	},

	changeShowTimestamps() {
		this.setState({showTimestamps: !this.state.showTimestamps});
	},

	changeAutoScroll() {
		let autoScroll = !this.state.autoScroll;
		this.setState({autoScroll});
	},

	renderMessage(message) {
		return <MessageLine key={message.key} message={message}/>;
	},

	onScroll() {
		if(!this.state.autoScroll) {
			return;
		}
		let container = ReactDOM.findDOMNode(this.refs.container);
		if(this.autoScrollTop !== container.scrollTop) {
			this.setState({autoScroll: false});
		}
	},

	render() {
		let logClassname = "log-output";
		if (!this.state.showTimestamps) {
			logClassname += " log-hide-timestamps";
		}
		return <div style={{height: "100%", width: "100%"}}>
			<div style={{height: "20px", padding: "2px 5px"}}>
				<label>
					<input type="checkbox" checked={this.state.showTimestamps} onChange={this.changeShowTimestamps}/>
					Show timstamps
				</label>
				<span className="pull-right">
					<label>
						<input type="checkbox" checked={this.state.autoScroll} onChange={this.changeAutoScroll}/> Auto-Scroll
					</label>
				</span>
			</div>
			<div ref="container" onScroll={this.onScroll} style={{height: "calc(100% - 30px)", width: "100%", overflowY: "scroll"}} className={logClassname}>
				<VirtualList items={this.state.messages} renderItem={this.renderMessage} itemHeight={12}
							 style={{height: "100px"}} container={this.state.container}/>
			</div>
		</div>;
	}
}));

