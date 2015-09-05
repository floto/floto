import { connect } from 'react-redux';

import {Table, Label, Button, SplitButton, MenuItem} from "react-bootstrap";

export default connect(state => {
	return {containers: state.manifest.containers}
})(React.createClass({

			render() {
				let containers = this.props.containers || [];
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap"}}>
						<div style={{flex: 1}}>
							<h2>Containers</h2>
							<Table bordered striped hover condensed>
								<tbody>
								{containers.map((container) =>
									<tr key={container.name}>
										<td><Label bsStyle='default'>{container.state || "unknown" }</Label></td>
										<td>
											<div style={{width: 100}}><SplitButton bsStyle="primary" bsSize="xs"
																				   title="Redeploy" id="redeploy">
												<MenuItem eventKey='1'>Action</MenuItem>
												<MenuItem eventKey='2'>Another action</MenuItem>
												<MenuItem eventKey='3'>Something else here</MenuItem>
											</SplitButton></div>
										</td>
										<td><Button bsStyle="success" bsSize="xs">Start</Button></td>
										<td><Button bsStyle="danger" bsSize="xs">Stop</Button></td>
										<td><Button bsStyle="danger" bsSize="xs">Purge Data</Button></td>
										<td style={{width: "100%"}}>{container.name}</td>
									</tr>)}
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




