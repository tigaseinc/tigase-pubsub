/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.pubsub;

//~--- non-JDK imports --------------------------------------------------------

import tigase.cluster.ClusterController;
import tigase.cluster.ClusterElement;
import tigase.cluster.ClusteredComponent;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;

import tigase.pubsub.cluster.ClusterNodeMap;
import tigase.pubsub.cluster.Command;
import tigase.pubsub.cluster.ViewNodeLoadCommand;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;

import tigase.server.Packet;

import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 *
 *
 * @version        5.0.0, 2010.02.04 at 12:50:29 GMT
 * @author         Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubClusterComponent extends PubSubComponent implements ClusteredComponent {
	private static final String METHOD_PRESENCE_COLLECTION = "pubsub.presenceCollection";
	private static final String METHOD_RESULT = "pubsub.result";
	private static final String METHOD_SET_OWNERSHIP = "pubsub.setOwnership";

	//~--- fields ---------------------------------------------------------------

	private final Set<JID> cluster_nodes = new LinkedHashSet<JID>();

	// sprawić by zapominało po iluśtam sekundach
	protected final ListCache<String, Command> waitingsCommands = new ListCache<String,
																																	Command>(1000,
					1000 * 60);
	protected final ClusterNodeMap nodeMap;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 */
	public PubSubClusterComponent() {
		super();
		this.log = Logger.getLogger(this.getClass().getName());
		log.config("PubSubCluster Component starting");
		nodeMap = new ClusterNodeMap(cluster_nodes);
	}

	//~--- get methods ----------------------------------------------------------

	protected static String[] getParameters(final String name,
					final Map<String, String> allMethodParams) {
		List<String> nodesNames = new ArrayList<String>();

		for (Map.Entry<String, String> pps : allMethodParams.entrySet()) {
			if (pps.getKey().startsWith(name)) {
				nodesNames.add(pps.getValue());
			}
		}

		return nodesNames.toArray(new String[] {});
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param admins
	 * @param pubSubDAO
	 * @param createPubSubRepository
	 * @param defaultNodeConfig
	 *
	 * @throws RepositoryException
	 * @throws TigaseDBException
	 * @throws UserNotFoundException
	 */
	@Override
	public void initialize(String[] admins, PubSubDAO pubSubDAO,
												 IPubSubRepository createPubSubRepository,
												 LeafNodeConfig defaultNodeConfig)
					throws UserNotFoundException, TigaseDBException, RepositoryException {
		super.initialize(admins, pubSubDAO, createPubSubRepository, defaultNodeConfig);
		log.info(getComponentId() + " reads all nodes");

		String[] nodes = this.directPubSubRepository.getNodesList();

		nodeMap.addPubSubNode(nodes);
		this.adHocCommandsModule.register(new ViewNodeLoadCommand(this.config, this.nodeMap));
	}

	/**
	 * Method description
	 *
	 *
	 * @param node
	 */
	@Override
	public void nodeConnected(String node) {
		log.finest("Node connected: " + node + " (" + getName() + "@" + node + ")");
		cluster_nodes.add(JID.jidInstanceNS(getName(), node, null));
		sendAvailableJidsToNode(getName() + "@" + node);
	}

	/**
	 * Method description
	 *
	 *
	 * @param node
	 */
	@Override
	public void nodeDisconnected(String node) {

		// TODO Auto-generated method stub
	}

	/**
	 * Method description
	 *
	 *
	 * @param node_hostnames
	 */
	public void nodesDisconnected(Set<String> node_hostnames) {
		for (String node : node_hostnames) {
			log.finest("Node disconnected: " + node + " (" + getName() + "@" + node + ")");
			cluster_nodes.remove(JID.jidInstanceNS(getName(), node, null));
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
	@Override
	public void processPacket(final Packet packet) {
		log.finest("Received by " + getComponentId() + ": " + packet.getElement().toString());

		if ((packet.getElemName() == ClusterElement.CLUSTER_EL_NAME)
				|| ((packet.getElemName() == ClusterElement.CLUSTER_EL_NAME)
						&& (packet.getElement().getXMLNS() == ClusterElement.XMLNS))) {
			log.finest("Handling as internal cluster message");

			final ClusterElement clel = new ClusterElement(packet.getElement());
			List<Element> elements = clel.getDataPackets();

			if (clel.getMethodName() != null) {
				try {
					processMethodCall(clel);
				} catch (Exception e) {
					log.throwing("PubSub Service", "processPacket (remote method call)", e);
					e.printStackTrace();

					try {
						addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
										e.getMessage(), true));
					} catch (PacketErrorTypeException e1) {
						e1.printStackTrace();
						log.throwing("PubSub Service", "processPacket (sending internal-server-error)", e);
					}
				}
			} else {
				for (Element element : elements) {
					try {
						super.processPacket(Packet.packetInstance(element));
					} catch (TigaseStringprepException ex) {
						log.info("Packet addressing problem, stringprep failed: " + element);
					}
				}
			}
		} else {
			if ((this.cluster_nodes == null) || (this.cluster_nodes.size() == 0)) {
				super.processPacket(packet);
			} else {
				Element element = packet.getElement();

				if (element.getName().equals("presence")) {
					sentBroadcast(packet);
				} else {
					String node = extractNodeName(element);

					if (node != null) {
						if (isProcessedLocally(node)) {
							super.processPacket(packet);
						} else {
							String clusterNode = this.nodeMap.getClusterNodeId(node);

							if (clusterNode == null) {
								synchronized (this) {
									clusterNode = this.nodeMap.getNewOwnerOfNode(node);

									String uuid = UUID.randomUUID().toString();

									this.nodeMap.assign(clusterNode, node);

									final String n = clusterNode;

									waitingsCommands.put(uuid, new Command() {
										public void execute() {
											sentToNode(packet, n);
										}
									});
									sendOwnershipInformation(uuid, clusterNode, node);
								}
							} else {
								log.finest("Cluster node " + getComponentId() + " received PubSub node '"
													 + node + "' and sent it to cluster node [" + clusterNode + "]");
								sentToNode(packet, clusterNode);
							}
						}
					} else {
						log.finest("Cluster node " + getComponentId()
											 + " received stanza without node name");
						super.processPacket(packet);
					}
				}
			}
		}
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param clController
	 */
	@Override
	public void setClusterController(ClusterController clController) {

		// TODO Auto-generated method stub
	}

	//~--- get methods ----------------------------------------------------------

//@Override
//public String getComponentId() {
//  String name;
//  if (System.getProperty("test", "no").equals("yes")) {
//    name = super.getComponentId().replace("@", ".");
//  } else
//    name = super.getComponentId();
//
//  return name;
//}
	protected String getFirstClusterNode() {
		String cluster_node = null;

		for (JID node : cluster_nodes) {
			if ( !node.equals(getComponentId())) {
				cluster_node = node.toString();

				break;
			}
		}

		return cluster_node;
	}

	//~--- methods --------------------------------------------------------------

	@Override
	protected void init() {
		if (System.getProperty("test", "no").equals("yes")) {
			final Set<String> n = new HashSet<String>();

			n.add("pubsub.sphere");
			n.add("pubsub1.sphere");
			n.add("pubsub2.sphere");

			// n.add("pubsub3.sphere");
			final String msh = "********** !!!  TEST ENVIROMENT !!! **********";

			System.out.println(msh);
			log.config(msh);

			for (String string : n) {
				log.config("Test Node connected: " + string);
				cluster_nodes.add(JID.jidInstanceNS(string));
			}
		}

		super.init();
		log.config("PubSubCluster component configured.");
	}

	protected void processMethodCall(ClusterElement clel)
					throws RepositoryException, PacketErrorTypeException {
		final String methodName = clel.getMethodName();
		final Map<String, String> methodParams = clel.getAllMethodParams();
		final String uuid = methodParams.get("uuid");

		if (clel.getFirstNode().equals(getComponentId())) {
			final Command command = waitingsCommands.remove(uuid);

			if (command != null) {
				command.execute();
			}
		} else {
			if (METHOD_PRESENCE_COLLECTION.equals(methodName)) {
				String[] jids = getParameters("jid", methodParams);

				for (String jid : jids) {
					this.presenceCollectorModule.addJid(jid);
				}
			} else {
				if (METHOD_SET_OWNERSHIP.equals(methodName)) {
					final String clusterNode = methodParams.get("clusterNodeId");
					final String pubsubNode = methodParams.get("pubsubNodeName");

					this.nodeMap.assign(clusterNode, pubsubNode);

					final boolean sent = sentToNextNode(clel);
				} else {
					throw new RuntimeException("Unsupported method " + methodName);
				}
			}
		}
	}

	protected void sendAvailableJidsToNode(final String node) {
		Map<String, String> params = new HashMap<String, String>();
		int counter = 0;

		for (String jid : presenceCollectorModule.getAllAvailableJids()) {
			++counter;
			params.put("jid." + counter, jid);

			if (params.size() > 99) {
				ClusterElement call =
					ClusterElement.createClusterMethodCall(getComponentId().toString(),
								node,
								StanzaType.set,
								METHOD_PRESENCE_COLLECTION,
								params);

				try {
					addOutPacket(Packet.packetInstance(call.getClusterElement()));
					params = new HashMap<String, String>();
				} catch (TigaseStringprepException ex) {
					log.info("Packet addressing problem, stringprep failed: "
									 + call.getClusterElement());
				}
			}
		}

		if (params.size() != 0) {
			ClusterElement call = ClusterElement.createClusterMethodCall(getComponentId().toString(),
							node,
							StanzaType.set,
							METHOD_PRESENCE_COLLECTION,
							params);

			try {
				addOutPacket(Packet.packetInstance(call.getClusterElement()));
			} catch (TigaseStringprepException ex) {
				log.info("Packet addressing problem, stringprep failed: " + call.getClusterElement());
			}
		}
	}

	protected void sentBroadcast(final Packet packet) {
		log.finest("Send broadcast with: " + packet.toString());

		for (JID cNN : this.cluster_nodes) {
			sentToNode(packet, cNN.toString());
		}
	}

	protected boolean sentToNextNode(ClusterElement clel) {
		ClusterElement next_clel = ClusterElement.createForNextNode(clel,
						new LinkedList<JID>(cluster_nodes),
						getComponentId());

		if (next_clel != null) {

			// String nextNode = findNextUnvisitedNode(clel);
			// if (nextNode != null) {
			// ClusterElement next_clel = clel.nextClusterNode(nextNode);
			next_clel.addVisitedNode(getComponentId().toString());

			try {
				addOutPacket(Packet.packetInstance(next_clel.getClusterElement()));
			} catch (TigaseStringprepException ex) {
				log.info("Packet addressing problem, stringprep failed: "
								 + next_clel.getClusterElement());
			}

			return true;
		} else {
			return false;
		}
	}

	protected boolean sentToNextNode(Packet packet) {
		if (cluster_nodes.size() > 0) {
			JID sess_man_id = getComponentId();
			String cluster_node = getFirstClusterNode();

			if (cluster_node != null) {
				ClusterElement clel = new ClusterElement(sess_man_id.toString(),
								cluster_node,
								StanzaType.set,
								packet);

				clel.addVisitedNode(sess_man_id.toString());
				log.finest("Sending packet to next node [" + cluster_node + "]");

				try {
					addOutPacket(Packet.packetInstance(clel.getClusterElement()));
				} catch (TigaseStringprepException ex) {
					log.info("Packet addressing problem, stringprep failed: "
									 + clel.getClusterElement());
				}

				return true;
			}
		}

		return false;
	}

	protected boolean sentToNode(final Packet packet, final String cluster_node) {
		if (cluster_node.equals(getComponentId().toString())) {
			super.processPacket(packet);
		} else {
			if (cluster_nodes.size() > 0) {
				JID sess_man_id = getComponentId();

				if (cluster_node != null) {
					ClusterElement clel = new ClusterElement(sess_man_id.toString(),
									cluster_node,
									StanzaType.set,
									packet);

					clel.addVisitedNode(sess_man_id.toString());
					log.finest("Sending packet to next node [" + cluster_node + "]");

					try {
						addOutPacket(Packet.packetInstance(clel.getClusterElement()));
					} catch (TigaseStringprepException ex) {
						log.info("Packet addressing problem, stringprep failed: "
										 + clel.getClusterElement());
					}

					return true;
				}
			}
		}

		return false;
	}

	private String findNextUnvisitedNode(final ClusterElement clel) {
		final JID comp_id = getComponentId();

		if (cluster_nodes.size() > 0) {
			String next_node = null;

			for (JID cluster_node : cluster_nodes) {
				if ( !clel.isVisitedNode(cluster_node.toString()) &&!cluster_node.equals(comp_id)) {
					next_node = cluster_node.toString();
					log.finest("Found next cluster node: " + next_node);

					break;
				}
			}

			return next_node;
		}

		return null;
	}

	//~--- get methods ----------------------------------------------------------

	private boolean isProcessedLocally(final String node) {
		if (this.publishNodeModule.isPEPNodeName(node)) {
			return true;
		} else {
			if ("http://jabber.org/protocol/commands".equals(node)) {
				return true;
			}
		}

		return false;
	}

	//~--- methods --------------------------------------------------------------

	private void sendOwnershipInformation(final String uuid, final String clusterNode,
					final String pubsubNode) {
		String cluster_node = getFirstClusterNode();
		Map<String, String> params = new HashMap<String, String>();

		params.put("uuid", uuid);
		params.put("clusterNodeId", clusterNode);
		params.put("pubsubNodeName", pubsubNode);

		ClusterElement call = ClusterElement.createClusterMethodCall(getComponentId().toString(),
						cluster_node,
						StanzaType.set,
						METHOD_SET_OWNERSHIP,
						params);

		sentToNextNode(call);
	}

	private void sendResult(String firstNode, String uuid) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("uuid", uuid);

		ClusterElement call = ClusterElement.createClusterMethodCall(getComponentId().toString(),
						firstNode,
						StanzaType.result,
						METHOD_RESULT,
						params);

		try {
			addOutPacket(Packet.packetInstance(call.getClusterElement()));
		} catch (TigaseStringprepException ex) {
			log.info("Packet addressing problem, stringprep failed: " + call.getClusterElement());
		}
	}
}


//~ Formatted in Sun Code Convention


//~ Formatted by Jindent --- http://www.jindent.com
