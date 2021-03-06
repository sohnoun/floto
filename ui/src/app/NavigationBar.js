import {Navbar, Nav, NavItem, NavDropdown, CollapsibleNav, MenuItem, Button} from "react-bootstrap";
import {NavItemLink} from 'react-router-bootstrap';
import {Link} from 'react-router';
import { connect } from 'react-redux';

var Icon = require('react-fa');

import Switch from "../components/Switch.js";


export default connect(state => {
	return {serverState: state.serverState, clientState: state.clientState, site: state.manifest.site};
})(React.createClass({
	contextTypes: {
		actions: React.PropTypes.object.isRequired,
		router: React.PropTypes.object
	},

	componentDidMount() {
		this.unlistenToHistory = this.context.router.listen(() => {
			this.forceUpdate();
		});
	},


	recompileManifest() {
		this.context.actions.recompileManifest();
	},

	onChangeSafety(safetyArmed) {
		this.context.actions.changeSafety(safetyArmed);
	},


	render() {
		let isActive = this.context.router.isActive;
		let isCompiling = this.props.serverState.isCompiling;
		let site = this.props.site || {projectName: "?", projectRevision: "?"};
		let siteName = site.projectName || site.domainName;
		return <Navbar fixedTop fluid>
			<Navbar.Header>
				<Navbar.Brand>
					<a href="#"><span><img src="/img/floto-icon.svg" style={{height: 24}}/></span>&nbsp;floto</a>
				</Navbar.Brand>
				<Navbar.Toggle />
			</Navbar.Header>
			<Navbar.Collapse>
				<Nav navbar>
					<NavItem active={isActive("/containers")} href="#/containers"><Icon name="cubes"/>&nbsp;&nbsp;
						Containers</NavItem>
					<NavItem active={isActive("/hosts")} href="#/hosts"><Icon name="server"/>&nbsp;&nbsp;Hosts</NavItem>
					<NavItem active={isActive("/patches")} href="#/patches"><Icon name="file-archive-o"/>&nbsp;&nbsp;
						Patches</NavItem>
					<NavItem active={isActive("/documents")} href="#/documents"><Icon name="file-o"/>&nbsp;&nbsp;
						Documents</NavItem>
					<NavItem active={isActive("/tasks")} href="#/tasks"><Icon name="list"/>&nbsp;&nbsp;Tasks</NavItem>
					<NavDropdown title={<span><Icon name="download" />&nbsp;&nbsp;Export</span>}
								 id='basic-nav-dropdown'>
						<MenuItem href="api/export/container-logs">Container Logs</MenuItem>
						<MenuItem href="api/export/task-logs">Task Logs</MenuItem>
						<MenuItem href="api/export/build-logs">Build Logs</MenuItem>
						<li><a href="api/manifest"
							   download={`manifest-${siteName}-${site.projectRevision}.json`}>Manifest</a></li>
					</NavDropdown>
					<NavItem active={isActive("/manifest")} href="#/manifest"><Icon name="file-text-o"/>&nbsp;&nbsp;
						Manifest</NavItem>

					<form className="navbar-form navbar-left">
						<div className="form-group">
							<Button disabled={isCompiling} bsStyle='primary' bsSize='small'
									onClick={this.recompileManifest}
									style={{width: "10em", textAlign: "left"}}><Icon spin={isCompiling}
																					 name="cog"/>&nbsp;&nbsp;{isCompiling ? "Recompiling..." : "Recompile"}
							</Button>
							&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
							<Switch checked={this.props.clientState.safetyArmed} onChange={this.onChangeSafety}/>
						</div>
					</form>
				</Nav>

				<div className="nav navbar-nav navbar-right hidden-xs hidden-md hidden-sm hidden-lg visible-xl"
					 style={{textAlign: "center", paddingTop: "10px", paddingRight: "20px", height: "20px"}}>
				<span style={{color: site.siteColor}}>{siteName}{site.environment ?
					<span ng-if="site.environment"> ({site.environment})</span> : null}</span><br />
				<span style={{fontSize: "80%", position: "relative", top: "-4px"}}
					  className="text-muted">{site.projectRevision}</span>
				</div>
			</Navbar.Collapse>
		</Navbar>;
	}
}));


