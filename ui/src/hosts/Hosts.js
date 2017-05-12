import { connect } from 'react-redux';
import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
import DebounceInput from 'react-debounce-input';

const labelStyleMapping = {
	running: "success",
	stopped: "warning",
	"not-there": "danger"
};

let hostSortTypes = {
	none: {title: "Deployment order", sortFn: (hosts) =>  hosts },
	name: {title: "Name", sortFn: (hosts) => _.sortBy(hosts, "name") }
};

export default connect(state => {
	let hosts = state.hosts;
	if(!hosts || hosts.length === 0) {
		hosts = state.manifest.hosts;
	}
	return {
		clientState: state.clientState,
		hosts: hosts,
		hostFilter: state.hostFilter,
		selectedHost: state.selectedHost
	};
})(React.createClass({
			displayName: "Hosts",
			contextTypes: {
				actions: React.PropTypes.object.isRequired,
				router: React.PropTypes.object.isRequired
			},


			navigateToHost(hostName) {
				let newUrl = '/hosts/' + hostName;
				if (this.props.selectedHost) {
					let currentUrl = "/hosts/" + this.props.selectedHost.name;
					newUrl = this.props.location.pathname.replace(currentUrl, newUrl);
				}
				this.context.router.push({pathname: newUrl, query: this.props.location.query});
			},

			onChangeHostsSort(event, sort) {
				let query = this.props.location.query || {};
				if (sort === "none") {
					delete query.sort;
				}
				else {
					query.sort = sort;
				}
				this.context.router.push({pathname: this.props.location.pathname, query: query});
			},

			onChangeHostFilter(event) {
				let query = this.props.location.query || {};
				query.hostFilter = event.target.value.trim();
				if(query.hostFilter === "") {
					delete query.hostFilter;
				}
				this.context.router.push({pathname: this.props.location.pathname, query: query});
			},

			renderHost(host) {
				let safetyArmed = this.safetyArmed;
				let actions = this.context.actions;
				let rowClassName = null;
				let externalVm = false;
				if(host === this.selectedHost) {
					rowClassName = "info";
				}
				if(host.externalVm){
					externalVm = true;
				}
				
				let labelStyle = labelStyleMapping[host.state] || "default";
				return <tr key={host.name} onClick={this.navigateToHost.bind(this, host.name)} className={rowClassName}>
					<td><Label bsStyle={labelStyle}>{host.state || "unknown" }</Label></td>
					<td><Button bsStyle="primary" bsSize="xs" disabled={!safetyArmed || externalVm}
								onClick={actions.redeployHosts.bind(null, [host.name])}>Redeploy</Button>
					</td>
					<td><Button bsStyle="success" bsSize="xs" disabled={!safetyArmed || externalVm}
								onClick={actions.startHosts.bind(null, [host.name])}>Start</Button>
					</td>
					<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed || externalVm}
								onClick={actions.stopHosts.bind(null, [host.name])}>Stop</Button>
					</td>
					<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed || externalVm}
								onClick={actions.destroyHosts.bind(null, [host.name])}>Destroy</Button>
					</td>
					<td style={{width: "100%"}}>{host.name}
						{host.vmConfiguration.hypervisor.esxHost ? <span
							className="text-muted">@{host.vmConfiguration.hypervisor.esxHost}</span> : null}
						{externalVm ? <span className="text-muted"> (unmanaged)</span> : null}</td>
				</tr>;
			},

			render() {
				let actions = this.context.actions;
				let hosts = this.props.hosts || [];
				let allHosts = hosts;
				let selectedHost = this.selectedHost = this.props.selectedHost || {};
				let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
				let query = (this.props.location.query || {});
				let hostSortKey = query.sort || "none";
				let hostSortType = hostSortTypes[hostSortKey] || hostSortTypes.none;
				hosts = hostSortType.sortFn(hosts);

				let hostFilter = query.hostFilter || "";
				let hostFilterError = null;
				let hostFilterRegex = null;
				try {
					hostFilterRegex = new RegExp(hostFilter, "i");
				} catch(error) {
					console.log(error);
					hostFilterError = ""+error;
				}

				if(hostFilterRegex !== null) {
					hosts = _.filter(hosts, (host) => hostFilterRegex.test(host.name));
				}

				//patch-maker always at first position
				let patchMaker = _.find(hosts, (host) => host.name == "patch-maker");
				hosts = _.filter(hosts, (host) => host.name != "patch-maker");
				if (patchMaker != null) hosts.splice(0, 0, patchMaker);

				let controlledVmNames = [];

				_.forEach(hosts, (host) => {
					if (!host.externalVm) {
						controlledVmNames.push(host.name);
					}
				});
				
				let hostsCountName = "all";
				if(controlledVmNames.length != allHosts.length) {
					hostsCountName = controlledVmNames.length;
				}

				let buttonStyle = {width: "100px"};
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", width: "50%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px", marginRight: "10px"}}>
								<h2>Hosts <span className="text-muted">({hosts.length})</span>
								<span className="pull-right"><small>Sort by:&nbsp;&nbsp;&nbsp;</small>
									<DropdownButton bsStyle="default" title={hostSortType.title}
													id="hosts-sort"
													onSelect={this.onChangeHostsSort}>
										{_.map(hostSortTypes, (sortType, key) =>
											<MenuItem key={key} eventKey={key}><span
												style={{fontWeight: key === hostSortKey ? "bold": "normal"}}>{sortType.title}</span></MenuItem>)}
									</DropdownButton>
								</span>
								</h2>
								<ButtonGroup>
									<Button onClick={actions.loadHostStates}>Refresh</Button>
									<Button bsStyle="primary" onClick={() => actions.redeployHosts(controlledVmNames)}
											disabled={!safetyArmed}>{"Redeploy "+hostsCountName+" hosts"}</Button>
									<Button bsStyle="success" onClick={() => actions.startHosts(controlledVmNames)}
											disabled={!safetyArmed} style={buttonStyle} >Start {hostsCountName}</Button>
									<Button bsStyle="danger" onClick={() => actions.stopHosts(controlledVmNames.slice().reverse())}
											disabled={!safetyArmed} style={buttonStyle}>Stop {hostsCountName}</Button>
									<span className={hostFilterError?"has-warning":""}>
									<DebounceInput
										style={{display: "inline-block", width: "160px", marginLeft: "5px"}}
										type="text"
										placeholder="Filter hosts"
										title={hostFilterError}
										value={hostFilter}
										debounceTimeout={(allHosts.length > 500)? 500 : 0}
										onChange={this.onChangeHostFilter}
										className="form-control"
									/>
									</span>
								</ButtonGroup>
							</div>
							<div style={{flex: "1 1 auto", overflowY: "scroll"}}>
								<Table bordered striped hover condensed style={{cursor: "pointer"}}>
									<tbody>
									{hosts.map(this.renderHost)}
									</tbody>
								</Table>
							</div>
						</div>
						<div key={selectedHost.name} style={{flex: 1, paddingLeft: 20, height: "100%", width: "50%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);


