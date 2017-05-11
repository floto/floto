import React from 'react';
import ReactDOM from 'react-dom';

class HorizontalResizeLayout extends React.Component {

	constructor( props ) {
		super( props );

		this.state = {
			isSeparatorActive: false,
			leftWidth: "50%"
		};
	}

	shouldComponentUpdate( nextProps, nextState ) {

		if( this.state.isSeparatorActive !== nextState.isSeparatorActive ) {

			return false;
		}

		else {
			return true;
		}
	}

	render() {
		console.log("Rendering layout");

		let children = this.props.children;

		if( 2 !== children.length ) {
			return this.printErrorMessage( "ResizeLayout only supports 2 children!" );
		}

		const mainStyle = {
			//display: "flex",
			//flexboxDirection: "row",
			//flexWrap: "nowrap",
			height: "100%"
		};

		const leftChildStyle = {
			width: this.state.leftWidth
		};

		/*
		const rightChildStyle = {
			width: this.resizeLayoutElement ?????????????????????
		};
		*/

		let leftChild = children[ 0 ];
		let rightChild = children[ 1 ];

		return <div style={mainStyle}
					onMouseMove={this.handleSeparatorMovement.bind(this)}
					onMouseUp={this.stopSeparatorMovement.bind(this)}
					ref={(element) => this.resizeLayoutElement = element}>

					<div style={leftChildStyle}>
						{leftChild}
					</div>

					<ResizeSeparator defaultColor="#ffffff"
									 rolloverColor="#666666"
									 moveColor="#659fff"
									 invokeSeparatorMovement={this.invokeSeparatorMovement.bind(this)}/>

					<div style={{width:"100%"}}>
						{rightChild}
					</div>

				</div>;
	}

	printErrorMessage( errorMessage ) {

		console.error( errorMessage );

		let errorStyle = {
			color: 'red'
		};

		return <div ref="resizeMaster">
			<span style={errorStyle}>{errorMessage}</span>
		</div>;
	}

	invokeSeparatorMovement() {

		this.setState({
			isSeparatorActive: true
		});
	}

	stopSeparatorMovement() {

		if( true === this.state.isSeparatorActive ) {

			this.setState({
				isSeparatorActive: false
			});
		}
	}

	handleSeparatorMovement( event ) {

		if( true === this.state.isSeparatorActive ) {

			let boundary = this.resizeLayoutElement.getBoundingClientRect();

			const newLeftWidth = ( event.pageX - boundary.left ) + "px";

			console.log(newLeftWidth);

			this.setState({
				leftWidth: newLeftWidth
			});
		}
	}
}

class ResizeLayout extends React.Component {

	constructor( props ) {
		super( props );
	}

	render() {

	}
}

class ResizeSeparator extends React.Component {

	constructor( props ) {
		super( props );

		this.state = {
			isRolloverActive: false,
			isMouseDownActive: false,
			width: 100,
			horizontalMargin: 4
		};
	}

	validateProperties( props ) {

		if( undefined === props.invokeSeparatorMovement ) {
			console.error( "Missing property for ResizeSeparator: invokeSeparatorMovement!" );
		}

		if( undefined === props.defaultColor ) {
			console.error( "Missing property for ResizeSeparator: defaultColor!" );
		}

		if( undefined === props.rolloverColor ) {
			console.error( "Missing property for ResizeSeparator: rolloverColor!" );
		}

		if( undefined === props.moveColor ) {
			console.error( "Missing property for ResizeSeparator: defaultColor!" );
		}
	}

	shouldComponentUpdate( nextProps, nextState ) {

		return true;
	}

	render() {
		console.log("Rendering separator");

		const rolloverColor = this.state.isRolloverActive ? this.props.rolloverColor : this.props.defaultColor;
		const separatorColor = this.state.isMouseDownActive ? this.props.moveColor : rolloverColor;

		let separatorStyle = {
			display: "block",
			backgroundColor: separatorColor,
			width: this.state.width + "px",
			borderRadius: "4px",
			marginLeft: this.state.horizontalMargin + "px",
			marginRight: this.state.horizontalMargin + "px"
		};

		return <div style={separatorStyle}
					onMouseOver={this.handleMouseOver.bind(this)}
					onMouseOut={this.handleMouseOut.bind(this)}
					onMouseDown={this.handleMouseDown.bind(this)}
					onMouseUp={this.handleMouseUp.bind(this)}/>
	}

	handleMouseOver() {
		this.setState({
			isRolloverActive: true
		});
	}

	handleMouseOut() {
		this.setState({
			isRolloverActive: false,
			isMouseDownActive: false
		});
	}

	handleMouseDown() {
		this.setState({
			isMouseDownActive: true
		});
		this.props.invokeSeparatorMovement();

		return false;	// Prevent mouse from select a text.
	}

	handleMouseUp() {
		this.setState({
			isMouseDownActive: false
		});
	}
}

export default HorizontalResizeLayout;
