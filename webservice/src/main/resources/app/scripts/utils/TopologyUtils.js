import React from 'react';
import _ from 'lodash';
import {Components,toastOpt} from './Constants';
import d3 from 'd3';
import TopologyREST from '../rest/TopologyREST';
import FSReactToastr from '../components/FSReactToastr';
//Sources
import SourceNodeForm from '../containers/Streams/TopologyEditor/SourceNodeForm';
//Processors
import ProcessorNodeForm from '../containers/Streams/TopologyEditor/ProcessorNodeForm';
import RulesNodeForm from '../containers/Streams/TopologyEditor/RulesNodeForm';
import SplitNodeForm from '../containers/Streams/TopologyEditor/SplitNodeForm';
import StageNodeForm from '../containers/Streams/TopologyEditor/StageNodeForm';
import JoinNodeForm from '../containers/Streams/TopologyEditor/JoinNodeForm';
import CustomNodeForm from '../containers/Streams/TopologyEditor/CustomNodeForm';
import NormalizationNodeForm from '../containers/Streams/TopologyEditor/NormalizationNodeForm';
import WindowingAggregateNodeForm from '../containers/Streams/TopologyEditor/WindowingAggregateNodeForm';
import BranchNodeForm from '../containers/Streams/TopologyEditor/BranchNodeForm';
//Sinks
import SinkNodeForm from '../containers/Streams/TopologyEditor/SinkNodeForm';
import CommonNotification from './CommonNotification';

const defineMarkers = function(svg){
	// define arrow markers for graph links
	let defs = svg.append('svg:defs')

	defs.append('svg:marker')
		.attr('id', 'end-arrow')
		.attr('viewBox', '0 -5 10 10')
		.attr('refX', "14")
		.attr('markerWidth', 6.5)
		.attr('markerHeight', 7.5)
		.attr('orient', 'auto')
		.append('svg:path')
		.attr('d', 'M0 -5 L10 0 L0 5')

	// define arrow markers for leading arrow
	defs.append('svg:marker')
		.attr('id', 'mark-end-arrow')
		.attr('viewBox', '0 -5 10 10')
		.attr('refX', 7)
		.attr('markerWidth', 6.5)
		.attr('markerHeight', 7.5)
		.attr('orient', 'auto')
		.append('svg:path')
		.attr('d', 'M0 -5 L10 0 L0 5')

        // define filter for gray(unconfigured) icons
	defs.append('svg:filter')
		.attr('id', 'grayscale')
		.append('feColorMatrix')
			.attr('type', 'saturate')
			.attr('values', '0');

        // define filter for node shadow
        var filter = defs.append('svg:filter')
                .attr('id', 'dropshadow')
                .attr('x', 0)
                .attr('y', 0)
                .attr('width', '200%')
                .attr('height', '200%');

                filter.append('feOffset')
                        .attr('result', 'offOut')
                        .attr('in', 'SourceAlpha')
                        .attr('dx', -4)
                        .attr('dy', -4);
                filter.append('feGaussianBlur')
                        .attr('result', 'blurOut')
                        .attr('in', 'offOut')
                        .attr('stdDeviation', 6);
                filter.append('feBlend')
                        .attr('in', 'SourceGraphic')
                        .attr('in2', 'blurOut')
                        .attr('mode', 'normal');
}

const isValidConnection = function(sourceNode, targetNode){
        let validConnection = true;
  //       if((sourceNode.currentType.toLowerCase() !== 'split' && targetNode.currentType.toLowerCase() === 'stage') ||
  //               (sourceNode.currentType.toLowerCase() === 'stage' && targetNode.currentType.toLowerCase() !== 'join')
  //         ){
  //               validConnection = false;
		// }
        return validConnection;
}

const createNode = function(topologyId, versionId, data, callback, metaInfo, paths, edges, internalFlags, uinamesList, setLastChange){
	let promiseArr = [];

	data.map((o)=>{
		let nodeType = this.getNodeType(o.parentType);
        let customName = o.uiname;

		//Dynamic Names of nodes
		while(uinamesList.indexOf(o.uiname) !== -1){
			let arr = o.uiname.split('-');
			let count = 1;
			if(arr.length > 1){
				count = parseInt(arr[1], 10) + 1;
			}
			o.uiname = arr[0]+'-'+count;
		}
		uinamesList.push(o.uiname);
		//
		//
        if(o.currentType.toLowerCase() === 'custom') {
            if(metaInfo.customNames) {
                metaInfo.customNames.push({
                    uiname: o.uiname,
                    customProcessorName: customName
                });
            } else {
                metaInfo.customNames = [{
                    uiname: o.uiname,
                    customProcessorName: customName
                }];
            }
        }
		let obj = {
			name: o.uiname,
			config: {},
                topologyComponentBundleId:o.topologyComponentBundleId
		}
        if(o.parentType === 'PROCESSOR'){
			obj["outputStreamIds"] = [];
		}
                promiseArr.push(TopologyREST.createNode(topologyId, versionId, nodeType, {body: JSON.stringify(obj)}));
	});

	//Make calls to create node or nodes
	Promise.all(promiseArr)
		.then((results)=>{

			results.map((o,i)=>{
				if(o.responseMessage !== undefined){
				FSReactToastr.error(
				<CommonNotification flag="error" content={o.responseMessage}/>, '', toastOpt)
				} else {
					data[i].nodeId = o.id;
				}
				if(i > 0){
					//Creating edge link
                    this.createEdge(data[i-1], data[i], paths, edges, internalFlags, callback, topologyId, versionId);
				}
			});
                        setLastChange(results[0].timestamp);
            this.saveMetaInfo(topologyId, versionId, data, metaInfo, callback);
		})

}

const saveMetaInfo = function(topologyId, versionId, nodes, metaInfo, callback){
    if(nodes){
        nodes.map((o)=>{
            let obj = {
                x: o.x,
                y: o.y,
                id: o.nodeId
            };
            metaInfo[this.getNodeType(o.parentType)].push(obj);
        })
    }

	let data = {
		topologyId: topologyId,
		data: JSON.stringify(metaInfo)
	};

        TopologyREST.putMetaInfo(topologyId, versionId, {body: JSON.stringify(data)})
		.then(()=>{
            if(callback) {
                //call the callback to update the graph
                callback();
            }
		})
}

const updateMetaInfo = function(topologyId, versionId, node, metaInfo){
	let metaArr = metaInfo[this.getNodeType(node.parentType)];
	let oldMetaObj = metaArr.filter((o)=>{return o.id === node.nodeId});
	if(oldMetaObj.length !== 0){
		oldMetaObj = oldMetaObj[0];
		oldMetaObj.x = node.x;
		oldMetaObj.y = node.y;
		if(node.streamId === 'failedTuplesStream'){
			oldMetaObj.streamId = node.streamId;
		} else {
			delete oldMetaObj.streamId;
		}
        } else {
                metaArr.push({
                        x: node.x,
                        y: node.y,
                        id: node.nodeId
                });
	}
        let data = { topologyId: topologyId, data: JSON.stringify(metaInfo) };
    TopologyREST.putMetaInfo(topologyId, versionId, {body: JSON.stringify(data)});
}

const removeNodeFromMeta = function(metaInfo, currentNode){
	let currentType = this.getNodeType(currentNode.parentType);
	let arr = metaInfo[currentType];
	let nodeMeta = arr.filter((o)=>{ return o.id === currentNode.nodeId});
        let customNameArr = metaInfo.customNames;
	nodeMeta.map((o)=>{
		arr.splice(arr.indexOf(o), 1);
	})
        if(customNameArr) {
        let customMeta = customNameArr.filter((o)=>{return o.uiname === currentNode.uiname});
        customMeta.map((o)=>{
                customNameArr.splice(customNameArr.indexOf(o), 1);
        })
        }
	return metaInfo;
}

const createEdge = function(mouseDownNode, d, paths, edges, internalFlags, callback, topologyId, versionId, getEdgeConfigModal, setLastChange){
	if(this.isValidConnection(mouseDownNode, d)){
		let newEdge = {
			source: mouseDownNode,
			target: d
		};
		let filtRes = paths.filter(function(d) {
			if (d.source === newEdge.target && d.target === newEdge.source) {
				edges.splice(edges.indexOf(d), 1);
			}
			return d.source === newEdge.source && d.target === newEdge.target;
		});
		if (!filtRes[0].length) {
            TopologyREST.getNode(topologyId, versionId, this.getNodeType(newEdge.source.parentType), newEdge.source.nodeId)
                .then((result)=>{
                        setLastChange(result.timestamp);
                    let nodeData = result;
                    if(newEdge.source.currentType.toLowerCase() === 'window' || newEdge.source.currentType.toLowerCase() === 'rule'){
                            nodeData.type = newEdge.source.currentType.toUpperCase();
                    }
                    if(getEdgeConfigModal){
                            getEdgeConfigModal(topologyId, versionId, newEdge, edges, callback, nodeData);
                    } else {
                            console.error("Cannot find getEdgeConfigModal: from createEdge:TopologyUtils");
                    }
                })
		}
	} else {
    FSReactToastr.error(
        <CommonNotification flag="error" content={mouseDownNode.currentType+" cannot be connected to " +d.currentType}/>, '', toastOpt)
	}
}

const getNodeType = function(parentType){
	switch(parentType){
                case 'SOURCE':
			return 'sources'
		break;
                case 'PROCESSOR':
			return 'processors'
		break;
                case 'SINK':
			return 'sinks'
		break;
	}
}

const deleteNode = function(topologyId, versionId, currentNode, nodes, edges, internalFlags, updateGraphMethod, metaInfo, uinamesList, setLastChange){
	let promiseArr = [],
		nodePromiseArr = [],
		callback = null,
		currentType = currentNode.currentType;

                //Get data of current node
        nodePromiseArr.push(TopologyREST.getNode(topologyId, versionId, this.getNodeType(currentNode.parentType), currentNode.nodeId))

                //Find out if the source of the current node is rules/windows
                //then update those processor by removing actions from it.
        let connectingNodes = edges.filter((obj)=>{ return obj.target == currentNode; });
        let actionsPromiseArr = [];
        connectingNodes.map((o,i)=>{
            if(o.source.currentType.toLowerCase() === 'rule' || o.source.currentType.toLowerCase() === 'window' ||
                o.source.currentType.toLowerCase() === 'branch'){
                let type = o.source.currentType.toLowerCase() === 'rule' ? 'rules' : (o.source.currentType.toLowerCase() === 'branch' ? 'branchrules' : 'windows');
                TopologyREST.getAllNodes(topologyId, versionId, type).then((results)=>{
                    results.entities.map((nodeObj)=>{
                        let actionsArr = nodeObj.actions,
                            actions = [],
                            hasAction = false;
                        actionsArr.map((a)=>{
                            if(a.name !== currentNode.uiname){
                                actions.push(a);
                            } else {
                                hasAction = true;
                            }
                        });
                        if(hasAction) {
                            nodeObj.actions = actions;
                            actionsPromiseArr.push(TopologyREST.updateNode(topologyId, versionId, type, nodeObj.id, {body: JSON.stringify(nodeObj)}));
                        }
                    });
                });
            }
        })

        Promise.all(actionsPromiseArr).
            then((results)=>{
                for(let i = 0; i < results.length; i++){
                    if(results[i].responseMessage !== undefined){
                        FSReactToastr.error(<CommonNotification flag="error" content={results[i].responseMessage}/>, '', toastOpt)
                    }
                }
            });

                Promise.all(nodePromiseArr)
                .then(results=>{
                        let nodeData = results[0];
                        setLastChange(results[0].timestamp);
                        //Delete streams of all nodes
                        results.map(result=>{
                                let node = result;
                                if(node.outputStreams){
                                        node.outputStreams.map(stream=>{
                                                if(stream.id){
                                                        promiseArr.push(TopologyREST.deleteNode(topologyId, 'streams', stream.id));
                                                }
                                        })
                                }
                        })


                        //Delete Rules incase of Rule Processor
                        if(nodeData.type === 'RULE'){
                                if(nodeData.config.properties.rules){
                                        nodeData.config.properties.rules.map(ruleId=>{
                                                promiseArr.push(TopologyREST.deleteNode(topologyId, 'rules', ruleId));
                                        })
				}
                        }

                        //Delete Window incase of Rule Processor
                        if(nodeData.type === 'WINDOW'){
                                if(nodeData.config.properties.rules){
                                        nodeData.config.properties.rules.map(ruleId=>{
                                                promiseArr.push(TopologyREST.deleteNode(topologyId, 'windows', ruleId));
                                        })
				}
                        }

                        //Delete Links
                        let edgeArr = this.getEdges(edges, currentNode);

                        edgeArr.map((o)=>{
                                promiseArr.push(TopologyREST.deleteNode(topologyId, 'edges', o.edgeId));
                        });

                        //Delete data from metadata
                        metaInfo = this.removeNodeFromMeta(metaInfo, currentNode);
                        let metaData = {
                                topologyId: topologyId,
                                data: JSON.stringify(metaInfo)
                        };
                            promiseArr.push(TopologyREST.putMetaInfo(topologyId, versionId, {body: JSON.stringify(metaData)}));

            //Delete current node
                        promiseArr.push(TopologyREST.deleteNode(topologyId, this.getNodeType(currentNode.parentType), currentNode.nodeId));

                        //If needed to reset any processor on delete - it comes here or in callback
                        callback = function(){
                                // Graph related Operations
                                uinamesList.splice(uinamesList.indexOf(currentNode.uiname), 1);
                                nodes.splice(nodes.indexOf(currentNode), 1);
                                this.spliceLinksForNode(currentNode, edges);
                                internalFlags.selectedNode = null;
                                updateGraphMethod();
                        }.bind(this)

                        if(promiseArr.length > 0){
                                //Make calls to delete node or nodes
                                Promise.all(promiseArr)
                                        .then((results)=>{
                                                for(let i = 0; i < results.length; i++){
                                                        if(results[i].responseMessage !== undefined){
              FSReactToastr.error(
                  <CommonNotification flag="error" content={results[i].responseMessage}/>, '', toastOpt)
                                                        }
                                                }
                                                //call the callback
                                                callback();
                                        })
                        }

                })

}

const getEdges = function(allEdges, currentNode){
	return allEdges.filter((l)=>{
		return (l.source === currentNode || l.target === currentNode);
	});
}

const deleteEdge = function(selectedEdge, topologyId, versionId, internalFlags, edges, updateGraphMethod, setLastChange){
    let promiseArr = [TopologyREST.deleteNode(topologyId, 'edges', selectedEdge.edgeId),
				TopologyREST.getNode(topologyId, versionId, 'processors', selectedEdge.target.nodeId)];
    if(selectedEdge.source.currentType.toLowerCase() === 'rule' || selectedEdge.source.currentType.toLowerCase() === 'window'){
        promiseArr.push(TopologyREST.getNode(topologyId, versionId, 'processors', selectedEdge.source.nodeId));
    }
    Promise.all(promiseArr)
        .then((results)=>{
                setLastChange(results[0].timestamp);
		if(selectedEdge.target.currentType.toLowerCase() === 'join' ) {
			let joinProcessorNode = results[1];
			if(_.keys(joinProcessorNode.config.properties).length > 0) {
				joinProcessorNode.config.properties.joins = [];
				joinProcessorNode.config.properties.from = {};
				TopologyREST.updateNode(topologyId, versionId, 'processors', joinProcessorNode.id, {body: JSON.stringify(joinProcessorNode)});
			}
            }
            if(results.length === 3){
		//Find the connected source rule/window
                let rulePromises = [];
                let ruleProcessorNode = results[2];
                let t = selectedEdge.source.currentType.toLowerCase();
                let type = t === 'window' ? 'windows' : (t === 'rule' ? 'rules' : 'branchrules');
                if(ruleProcessorNode.config.properties.rules){
                    ruleProcessorNode.config.properties.rules.map(ruleId=>{
                        rulePromises.push(TopologyREST.getNode(topologyId, versionId, type, ruleId));
                    })
                }
                Promise.all(rulePromises)
                    .then(rulesResults=>{
                        rulesResults.map(ruleEntity=>{
                            let rule = ruleEntity;
                            if(rule.actions){
                                //If source rule has target notification inside rule action,
                                //then remove and update the rules/window.
                                let index = null;
                                rule.actions.map((a, i)=>{
                                    if(a.name === selectedEdge.target.uiname){
                                        index = i;
									}
                                                                })
                                if(index !== null){
                                    rule.actions.splice(index, 1);
                                    TopologyREST.updateNode(topologyId, versionId, type, rule.id, {body: JSON.stringify(rule)});
                                }
                            }
						})
                                        })
                }
            edges.splice(edges.indexOf(selectedEdge), 1);
            internalFlags.selectedEdge = null;
            updateGraphMethod();
        })
}

// remove edges associated with a node
const spliceLinksForNode = function(node, edges){
	let toSplice = this.getEdges(edges, node);
	toSplice.map(function(l) {
		edges.splice(edges.indexOf(l), 1);
	});
}

const replaceSelectNode = function(d3Node, nodeData, constants, internalFlags, rectangles){
	d3Node.classed(constants.selectedClass, true);
	if (internalFlags.selectedNode) {
		this.removeSelectFromNode(rectangles, constants, internalFlags);
	}
	internalFlags.selectedNode = nodeData;
}

const removeSelectFromNode = function(rectangles, constants, internalFlags){
	rectangles.filter(function(cd) {
		return cd.nodeId === internalFlags.selectedNode.nodeId;
	}).classed(constants.selectedClass, false);
	internalFlags.selectedNode = null;
}


const replaceSelectEdge = function(d3, d3Path, edgeData, constants, internalFlags, paths){
	d3Path.classed(constants.selectedClass, true);
	if (internalFlags.selectedEdge) {
		this.removeSelectFromEdge(d3, paths, constants, internalFlags);
	}
	internalFlags.selectedEdge = edgeData;
}

const removeSelectFromEdge = function(d3, paths, constants, internalFlags) {
	let path = paths.filter(function(cd) {
		return cd === internalFlags.selectedEdge;
	});
	let selectedPath = path[0][0];
	d3.select(selectedPath.previousSibling).classed(constants.selectedClass, false);
	internalFlags.selectedEdge = null;
}

const defineLinePath = function(p1, p2, flag){
	let segments = [],
		sourceX = p1.x,
		sourceY = p1.y,
		targetX = p2.x,
		targetY = p2.y;

	segments.push("M"+sourceX+','+sourceY);
	if(!flag){
		if(sourceX < targetX && sourceY === targetY){
			segments.push("H"+targetX);
		}else if(sourceX > targetX){
			segments.push("H"+(sourceX+20));
			segments.push("V"+((sourceY+targetY)/2));
			segments.push("H"+(targetX-20));
			segments.push("V"+(targetY));
			segments.push("H"+(targetX));
		} else {
			segments.push("H"+((sourceX+targetX)/2));
			segments.push("V"+(targetY));
			segments.push("H"+(targetX));
	  	}
	}else{
		segments.push("V"+(targetY));
		segments.push("H"+(targetX));
	}
	return segments.join(' ');
}

const showNodeModal = function(ModalScope, setModalContent, node, updateGraphMethod, allNodes, edges, linkShuffleOptions){
	let currentEdges = this.getEdges(edges, node);
	let scope = ModalScope(node);
    setModalContent(node, updateGraphMethod, this.getConfigContainer(node, scope.configData, scope.editMode, scope.topologyId, scope.versionId, currentEdges, allNodes, linkShuffleOptions, edges, updateGraphMethod));
}

const getConfigContainer = function(node, configData, editMode, topologyId, versionId, currentEdges, allNodes, linkShuffleOptions, edges, updateGraphMethod){
	let nodeType = this.getNodeType(node.parentType);
	let sourceNodes = [], targetNodes = [];
        currentEdges.map((e)=>{
		if(e.target.nodeId === node.nodeId){
			//find source node of parser
			sourceNodes.push(e.source);
		} else if(e.source.nodeId === node.nodeId){
			//find target node of parser
			targetNodes.push(e.target)
		}
	});
    if(node.parentType === 'SOURCE'){
        return () => {
            return <SourceNodeForm
                ref="ConfigModal"
                nodeData={node}
                configData={configData}
                editMode={editMode}
                nodeType={nodeType}
                topologyId={topologyId}
                versionId={versionId}
                targetNodes={targetNodes}
                linkShuffleOptions={linkShuffleOptions}
                />;
		}
    } else if(node.parentType === 'SINK'){
        return () => {
            return <SinkNodeForm
                ref="ConfigModal"
                nodeData={node}
                configData={configData}
                editMode={editMode}
                nodeType={nodeType}
                topologyId={topologyId}
                versionId={versionId}
                sourceNodes={sourceNodes}
            />;
        }
    } else if(node.parentType === 'PROCESSOR'){
        let childElement = null;
        switch(node.currentType.toUpperCase()){
            case 'RULE': //Rule
                childElement = () => { return <RulesNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    sourceNode={sourceNodes}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                />};
            break;
            case 'CUSTOM': //Custom
                childElement = () => { return <CustomNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    sourceNode={sourceNodes[0]}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                />};
            break;
            case 'NORMALIZATION': //Normalization
                childElement = () => { return <NormalizationNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                    currentEdges={currentEdges}
                />};
            break;
            case 'SPLIT': //Split
                childElement = () => { return <SplitNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    sourceNode={sourceNodes[0]}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                />};
            break;
            case 'STAGE': //Stage
                childElement = () => { return <StageNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                    currentEdges={currentEdges}
                />};
            break;
            case 'JOIN': //Join
                childElement = () => { return <JoinNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    sourceNode={sourceNodes}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                    currentEdges={currentEdges}
                />};
            break;
            case 'WINDOW': //Windowing
                childElement = () => { return <WindowingAggregateNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    sourceNode={sourceNodes[0]}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                    currentEdges={currentEdges}
                />};
            break;
            case 'BRANCH': //Branch
                childElement = () => { return <BranchNodeForm
                    ref="ProcessorChildElement"
                    nodeData={node}
                    configData={configData}
                    editMode={editMode}
                    nodeType={nodeType}
                    topologyId={topologyId}
                    versionId={versionId}
                    sourceNode={sourceNodes}
                    targetNodes={targetNodes}
                    linkShuffleOptions={linkShuffleOptions}
                    graphEdges={edges}
                    updateGraphMethod={updateGraphMethod}
                />};
            break;
        }
        return () => {
            return <ProcessorNodeForm
                ref="ConfigModal"
                nodeData={node}
                editMode={editMode}
                nodeType={nodeType}
                topologyId={topologyId}
                versionId={versionId}
                sourceNodes={sourceNodes}
                getChildElement={childElement}
            />;
        }
    }
}

const MouseUpAction = function(topologyId, versionId, d3node, d, metaInfo, internalFlags, constants, dragLine, paths, allNodes, edges, linkShuffleOptions, updateGraphMethod, elementType, getModalScope, setModalContent, rectangles, getEdgeConfigModal, setLastChange){
	// reset the internalFlags
	internalFlags.shiftNodeDrag = false;
	d3node.classed(constants.connectClass, false);

	var mouseDownNode = internalFlags.mouseDownNode;
	var hasSource = edges.filter((e)=>{return e.target.nodeId === d.nodeId});

    //cannot connect from unconfigured node
    if(!internalFlags.addEdgeFromNode) {
        internalFlags.addEdgeFromNode = true;
        return;
    }

	// if (!mouseDownNode) return;

	dragLine.classed("hidden", true);

	if (mouseDownNode && mouseDownNode !== d) {
		// we're in a different node: create new edge for mousedown edge and add to graph
        if(hasSource.length && d.currentType.toLowerCase() === 'branch') {
			FSReactToastr.warning(<strong>Edge cannot be connected to Branch.</strong>);
		} else {
                        this.createEdge(mouseDownNode, d, paths, edges, internalFlags, updateGraphMethod, topologyId, versionId, getEdgeConfigModal, setLastChange);
		}
        this.updateMetaInfo(topologyId, versionId, d, metaInfo);
	} else {
		if(elementType === 'rectangle'){
			// we're in the same node
			if (internalFlags.justDragged) {
				// dragged, not clicked
				internalFlags.justDragged = false;
			} else {
				// clicked, not dragged
				if(d3.event && d3.event.type === 'dblclick'){
                    let hasSource = edges.filter((e)=>{return e.target.nodeId === d.nodeId});
                    if(d.parentType === 'SOURCE' || hasSource.length) {
                        this.showNodeModal(getModalScope, setModalContent, d, updateGraphMethod, allNodes, edges, linkShuffleOptions);
                    } else {
                    	FSReactToastr.warning(<strong>Connect and configure a source component</strong>);
                    }
				} else {
					// we're in the same node
					if (internalFlags.selectedEdge) {
						this.removeSelectFromEdge(d3, paths, constants, internalFlags);
					}
					var prevNode = internalFlags.selectedNode;

					if (!prevNode || prevNode.nodeId !== d.nodeId) {
						this.replaceSelectNode(d3node, d, constants, internalFlags, rectangles);
					} else {
						this.removeSelectFromNode(rectangles, constants, internalFlags);
					}
				}
			}
		} else if(elementType === 'circle'){
			// we're in the same node
			if (internalFlags.selectedEdge) {
				this.removeSelectFromEdge(d3, paths, constants, internalFlags);
			}
			var prevNode = internalFlags.selectedNode;

			if (!prevNode || prevNode.nodeId !== d.nodeId) {
				this.replaceSelectNode(d3node, d, constants, internalFlags, rectangles);
			} else {
				this.removeSelectFromNode(rectangles, constants, internalFlags);
			}
		}
	}
	internalFlags.failedTupleDrag = false;
	internalFlags.mouseDownNode = null;
	return;
}

const setShuffleOptions = function(linkConfigArr){
	let options = [];
	linkConfigArr.map((o)=>{
		options.push({
			label: o.subType, value: o.subType
		})
	});
	return options;
}

const syncNodeData = function(sources, processors, sinks, metadata, sourcesBundle, processorsBundle, sinksBundle){
	let nodeArr = [];
        this.generateNodeData(sources, sourcesBundle, metadata.sources, nodeArr);
        this.generateNodeData(processors, processorsBundle, metadata.processors, nodeArr);
        this.generateNodeData(sinks, sinksBundle, metadata.sinks, nodeArr);
	return nodeArr;
}

const capitalizeFirstLetter = function(string){
	string = string.toLowerCase();
	return string.charAt(0).toUpperCase() + string.slice(1);
}

const generateNodeData = function(nodes, componentBundle, metadata, resultArr){
	for(let i = 0; i < nodes.length; i++){
                let componentObj = componentBundle.filter(c=>{return c.id === nodes[i].topologyComponentBundleId})[0];
                let currentType = this.capitalizeFirstLetter(componentObj.subType);
		let configuredFlag = _.keys(nodes[i].config.properties).length > 0 ? true : false;

		let currentMetaObj = metadata.filter((o)=>{return o.id === nodes[i].id});
		if(currentMetaObj.length === 0){
			console.error("Failed to get meta data");
		} else {
			currentMetaObj = currentMetaObj[0];
		}

		let nodeLabel = componentObj.subType;
		if(componentObj.subType.toLowerCase() === 'custom'){
			let config = componentObj.topologyComponentUISpecification.fields,
            name = _.find(config, {fieldName: "name"});
			nodeLabel = name.defaultValue || 'Custom';
		}

		let obj = {
			x: currentMetaObj.x,
			y: currentMetaObj.y,
			nodeId: nodes[i].id,
            parentType: componentObj.type,
			currentType: currentType,
			uiname: nodes[i].name,
            imageURL: 'styles/img/icon-'+componentObj.subType.toLowerCase()+'.png',
			isConfigured: configuredFlag,
            parallelismCount: nodes[i].config.properties.parallelism || 1,
            nodeLabel: nodeLabel,
            topologyComponentBundleId: componentObj.id
		}
		if(currentMetaObj.streamId){
			obj.streamId = currentMetaObj.streamId;
		}
		resultArr.push(obj);
	}
}

const syncEdgeData = function(edges, nodes){
	let edgesArr = [];
	edges.map((edge)=>{
		//Find source node
		let fromNode = nodes.filter((o)=>{ return o.nodeId === edge.fromId});
                if(fromNode.length !== 0)
			fromNode = fromNode[0];
		else console.error("From node is missing");

		//Find target node
		let toNode = nodes.filter((o)=>{ return o.nodeId === edge.toId});
                if(toNode.length !== 0)
			toNode = toNode[0];
		else console.error("To node is missing");

		edgesArr.push({
			source: fromNode,
			target: toNode,
                        edgeId: edge.id,
                        streamGrouping: edge.streamGroupings[0]
		});
	})
	return edgesArr;
}

const createLineOnUI = function(edge, constants){
	let arr = [],
		isFailedTupleflag = false;
	if(edge.target.streamId === "failedTuplesStream"){
		arr.push({x: (edge.source.x + constants.rectangleWidth / 2),y: (edge.source.y + constants.rectangleHeight)},
				 {x: edge.target.x, y: (edge.target.y + constants.rectangleHeight / 2)});
		isFailedTupleflag = true;
	} else {
		arr.push({x: (edge.source.x + constants.rectangleWidth),y: (edge.source.y + constants.rectangleHeight / 2)},
				 {x: edge.target.x, y: (edge.target.y + constants.rectangleHeight / 2)});
	}
	return this.defineLinePath(arr[0], arr[1], isFailedTupleflag);
}

const getNodeRectClass = function(data){
        if(data.parentType === 'SOURCE'){
		return 'source';
        } else if(data.parentType === 'PROCESSOR'){
		return 'processor';
        } else if(data.parentType === 'SINK'){
		return 'datasink';
	}
}

const getNodeImgRectClass = function(data){
        if(data.parentType === 'SOURCE'){
		return 'source-img';
        } else if(data.parentType === 'PROCESSOR'){
		return 'processor-img';
        } else if(data.parentType === 'SINK'){
		return 'datasink-img';
	}
}

const updateParallelismCount = function(topologyId, versionId, nodeData, setLastChange){
	let currentType = this.getNodeType(nodeData.parentType);
        TopologyREST.getNode(topologyId, versionId, currentType, nodeData.nodeId)
		.then((result)=>{
			let data = result;
			data.config.properties.parallelism = nodeData.parallelismCount;
                        TopologyREST.updateNode(topologyId, versionId, currentType, nodeData.nodeId, {body: JSON.stringify(data)})
                                .then((newNodeData)=>{
                                        setLastChange(newNodeData.timestamp);
                                })
		})
}

const topologyFilter = function(entities , filterValue){
  let matchFilter = new RegExp(filterValue , 'i');
    return entities.filter(filteredList => !filterValue || matchFilter.test(filteredList.topology.name))
}

const getEdgeData = function(data, topologyId, versionId, callback) {
    TopologyREST.getNode(topologyId, versionId, 'streams', data.streamGrouping.streamId)
        .then((result)=>{
            let obj = {
                streamName: result.streamId,
                grouping: data.streamGrouping.grouping,
                groupingFields: data.streamGrouping.fields,
                edgeData: data
            }
            callback(obj);
        })
}

export default {
	defineMarkers,
	isValidConnection,
	createNode,
	saveMetaInfo,
	updateMetaInfo,
	removeNodeFromMeta,
	createEdge,
	getNodeType,
	deleteNode,
	deleteEdge,
	getEdges,
	spliceLinksForNode,
	replaceSelectNode,
	removeSelectFromNode,
	replaceSelectEdge,
	removeSelectFromEdge,
	defineLinePath,
	showNodeModal,
	getConfigContainer,
	MouseUpAction,
	setShuffleOptions,
	syncNodeData,
	capitalizeFirstLetter,
	generateNodeData,
	syncEdgeData,
	createLineOnUI,
	getNodeRectClass,
	getNodeImgRectClass,
    updateParallelismCount,
    topologyFilter,
    getEdgeData
};
