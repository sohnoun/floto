import { connect } from 'react-redux';

import { History } from 'react-router';

import {Table, Label, Button, SplitButton, MenuItem, DropdownButton, ButtonGroup} from "react-bootstrap";
import TimeAgo from "react-timeago";
var Icon = require('react-fa');

import {formatBytes} from "../util/formatting.js";

export default connect(state => {
	return {
		selectedPatch: state.selectedPatch
	};
})(React.createClass({
			displayName: "Patches",
			mixins: [History],
			contextTypes: {
				actions: React.PropTypes.object.isRequired
			},


			render() {
				let actions = this.context.actions;
				let patches = this.props.patches || [];
				let patch = this.props.selectedPatch;
				if (!patch) {
					return null;
				}
				return <div style={{padding: "5px"}}>
					<h2>{patch.revision}</h2>
					<span className="text-muted">{patch.id}</span>
					<br/>
					<Button bsStyle="warning"
							onClick={() => actions.activatePatch(patch.id)}>Activate patch</Button>
					<span className="pull-right">
						<Button bsStyle="primary"
								onClick={() => actions.createIncrementalPatch(patch.id)}>Create incremental patch</Button>
					</span>
					<br/>
					<br/>
					<Table striped condensed style={{tableLayout: "fixed"}}>
						<colgroup>
							<col style={{width: "14em"}}/>
							<col style={{width: "100%"}}/>
						</colgroup>
						<tbody>
						<tr>
							<td>Created:</td>
							<td>{patch.creationDate} <span className="text-muted">(<span><TimeAgo date={patch.creationDate}/></span>)</span>
							</td>
						</tr>
						<tr>
							<td>Parent revision:</td>
							<td>{patch.parentRevision || '-'}{patch.parentId ? <span
								className="text-muted"><br /> ({patch.parentId})</span> : null}</td>
						</tr>
						<tr>
							<td>Patch size:</td>
							<td>{formatBytes(patch.patchSize)}</td>
						</tr>
						<tr>
							<td>Number of image layers:</td>
							<td>{patch.containedImageIds.length} contained / {patch.requiredImageIds.length} total</td>
						</tr>
						</tbody>
					</Table>
				</div>;

			}
		}
	)
);
