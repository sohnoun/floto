import { connect } from 'react-redux';

import { History } from 'react-router';

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
var Icon = require('react-fa');

export default connect(state => {
	return {
		clientState: state.clientState,
		hosts: state.manifest.hosts,
		selectedHost: state.selectedHost
	};
})(React.createClass({
			displayName: "Hosts",
			mixins: [History],
			contextTypes: {
				actions: React.PropTypes.object.isRequired
			},


			navigateToHost(hostName) {
				let newUrl = '/hosts/' + hostName;
				if (this.props.selectedHost) {
					let currentUrl = "/hosts/" + this.props.selectedHost.name;
					newUrl = this.props.location.pathname.replace(currentUrl, newUrl);
				}
				this.history.pushState(null, newUrl, this.props.location.query);
			},

			renderHost(host) {
				let safetyArmed = this.safetyArmed;
				let actions = this.context.actions;
				let rowClassName = null;
				if(host === this.selectedHost) {
					rowClassName = "info";
				}
				return <tr key={host.name} onClick={this.navigateToHost.bind(this, host.name)} className={rowClassName}>
					<td><Label bsStyle="default">{host.status || "unknown" }</Label></td>
					<td><Button bsStyle="primary" bsSize="xs" disabled={!safetyArmed}
								onClick={actions.destroyContainers.bind(null, host.name)}>Redeploy</Button>
					</td>
					<td><Button bsStyle="success" bsSize="xs" disabled={!safetyArmed}
								onClick={actions.destroyContainers.bind(null, host.name)}>Start</Button>
					</td>
					<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed}
								onClick={actions.destroyContainers.bind(null, host.name)}>Stop</Button>
					</td>
					<td><Button bsStyle="danger" bsSize="xs" disabled={!safetyArmed}
								onClick={actions.destroyContainers.bind(null, host.name)}>Destroy</Button>
					</td>
					<td style={{width: "100%"}}>{host.name}
						{host.vmConfiguration.hypervisor.esxHost ? <span
							className="text-muted">@{host.vmConfiguration.hypervisor.esxHost}</span> : null}</td>
				</tr>;
			},

			render() {
				let actions = this.context.actions;
				let hosts = this.props.hosts || [];
				let selectedHost = this.selectedHost = this.props.selectedHost || {};
				let safetyArmed = this.safetyArmed = this.props.clientState.safetyArmed;
				return <div style={{height: "100%"}}>
					<div style={{display: "flex", flexboxDirection: "row", flexWrap: "nowrap", height: "100%"}}>
						<div style={{flex: 1, height: "100%", display:"flex", flexDirection: "column"}}>
							<div style={{flex: "0 0 auto", marginBottom: "10px"}}>
								<h2>Hosts <span className="text-muted">({hosts.length})</span></h2>
								<ButtonGroup>
									<Button onClick={actions.loadContainerStates}>Refresh</Button>
									<Button bsStyle="primary" onClick={() => actions.startContainers(allContainerNames)}
											disabled={!safetyArmed}>Redeploy all</Button>
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
						<div key={selectedHost.name} style={{flex: 1, paddingLeft: 20, height: "100%"}}>
							{this.props.children}
						</div>
					</div>
				</div>;

			}
		}
	)
);




