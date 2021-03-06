import { createStore, combineReducers, compose, applyMiddleware } from 'redux';
import { Provider } from 'react-redux';
import { Redirect, Router, Route, useRouterHistory } from 'react-router';
import createHashHistory from 'history/lib/createHashHistory';
import createRouterHistory from 'react-router/lib/createRouterHistory';

import NavigationBar from "./NavigationBar.js";
import Application from "./Application";

import Containers from "../containers/Containers";
import Container from "../containers/Container";
import ContainerLogtail from "../containers/ContainerLogtail.js";

import Hosts from "../hosts/Hosts";
import Host from "../hosts/Host";
import Tasks from "../tasks/Tasks";
import Task from "../tasks/Task";
import Manifest from "../manifest/Manifest";

import Patches from "../patches/Patches";
import PatchInfo from "../patches/PatchInfo";

import Documents from "../documents/Documents";
import Document from "../documents/Document";

import FileViewer from "../components/FileViewer.js";

import reducers from '../reducers/reducers';

import taskService from "../tasks/taskService.js";

var initialState = {
	manifest: {},
	templateMap: {},
	serverState: {},
	clientState: {
		safetyArmed: true
	},
	flotoInfo: {}
};
const store = createStore(reducers, initialState);

let actions = require('../actions/actions.js');
const storeActions = _.mapValues(actions, (action, key) => (...args) => {actions[key](store, ...args);});

taskService.setActions(storeActions);

if (module.hot) {
	// Enable Webpack hot module replacement for reducers
	module.hot.accept('../reducers/reducers', () => {
		const nextRootReducer = require('../reducers/reducers');
		store.replaceReducer(nextRootReducer);
	});
	module.hot.accept('../actions/actions.js', () => {
		actions = require('../actions/actions.js');
	});
}

let history = useRouterHistory(createHashHistory)({
	queryKey: false
});

let routes = <Router history={history}>
		<Redirect from="/" to="/containers"/>
		<Route component={Application}>
			<Route path="/containers" component={Containers} onEnter={
					(nextState, transition)=>{
						actions.loadContainerStates(store);
					}
				}>
				<Route path=":containerName" component={Container} onEnter={
				(nextState, transition)=>{
					store.dispatch({type: "CONTAINER_SELECTED", payload: nextState.params.containerName});
					// workaround for file loading
					if(nextState.params.splat) {
						actions.loadContainerFile(store, nextState.params.containerName, nextState.params.splat);
					}
				}} onLeave={() => {
					store.dispatch({type: "CONTAINER_SELECTED", payload: null});
				}}>
					<Route path="log" component={ContainerLogtail} />
					<Route path="file/*" component={FileViewer} onEnter={
				(nextState, transition)=>{
					actions.loadContainerFile(store, nextState.params.containerName, nextState.params.splat);
				}}/>
				</Route>
			</Route>
			<Route path="/hosts" component={Hosts} onEnter={
					(nextState, transition)=>{
						actions.loadHostStates(store);
					}}>
				<Route path=":hostName" component={Host} onEnter={
				(nextState, transition)=>{
					store.dispatch({type: "HOST_SELECTED", payload: nextState.params.hostName});
					// workaround for file loading
					if(nextState.params.splat) {
						actions.loadHostFile(store, nextState.params.hostName, nextState.params.splat);
					}
				}} onLeave={() => {
					store.dispatch({type: "HOST_SELECTED", payload: null});
				}}>
					<Route path="log" component={ContainerLogtail} />
					<Route path="file/*" component={FileViewer} onEnter={
				(nextState, transition)=>{
					actions.loadHostFile(store, nextState.params.hostName, nextState.params.splat);
				}}/>
					</Route>
			</Route>
			<Route path="/patches" component={Patches} onEnter={
				(nextState, transition)=>{
					actions.loadPatches(store);
				}}>
				<Route path=":patchId" component={PatchInfo} onEnter={
				(nextState, transition)=>{
					actions.loadPatchInfo(store, nextState.params.patchId);
				}} onLeave={() => {
					store.dispatch({type: "PATCH_SELECTED", payload: null});
				}} />
			</Route>
			<Route path="/documents" component={Documents} >
				<Route path=":documentId" component={Document} onEnter={
				(nextState, transition)=>{
					actions.selectDocument(store, nextState.params.documentId);
				}} />
			</Route>
			<Route path="tasks" component={Tasks}>
				<Route path=":taskId" component={Task} onEnter={
				(nextState, transition)=>{
					store.dispatch({type: "TASK_SELECTED", payload: nextState.params.taskId});
				}} onLeave={() => {
					store.dispatch({type: "TASK_SELECTED", payload: null});
				}}/>
			</Route>
			<Route path="/manifest" component={Manifest}/>
		</Route>
	</Router>;

export default React.createClass({

	childContextTypes: {
		actions: React.PropTypes.object.isRequired
	},

	getChildContext: function () {
		return {actions: storeActions};
	},


	render() {
		return <div style={{}}>
			<Provider key="provider" store={store}>
				{routes}
			</Provider>
		</div>;
	}
});
window.addEventListener("load", function () {
	actions.refreshManifest(store);
	actions.getFlotoInfo(store);

	let config = {
		armed: false
	};

	window.floto.configureFns.forEach((fn) => fn(config));
	store.dispatch({type: "CONFIG_UPDATED", payload: config});
});


window.floto = {
	configure(fn) {
		this.configureFns.push(fn);
	},

	configureFns: []
};
