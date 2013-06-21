/*
 * UnsubscribeNodeModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.pubsub.modules;

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PacketWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

/**
 * Class description
 * 
 * 
 */
public class UnsubscribeNodeModule extends AbstractModule {
	private static final Criteria CRIT_UNSUBSCRIBE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("unsubscribe"));

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public UnsubscribeNodeModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_UNSUBSCRIBE;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @param packetWriter
	 * 
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public List<Packet> process(Packet packet, PacketWriter packetWriter) throws PubSubException {
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element unsubscribe = pubSub.getChild("unsubscribe");
		final String senderJid = element.getAttributeStaticStr("from");
		final String nodeName = unsubscribe.getAttributeStaticStr("node");
		final String jid = unsubscribe.getAttributeStaticStr("jid");
		final String subid = unsubscribe.getAttributeStaticStr("subid");

		try {
			AbstractNodeConfig nodeConfig = repository.getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			IAffiliations nodeAffiliations = repository.getNodeAffiliations(toJid, nodeName);
			UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid);
			UsersAffiliation affiliation = nodeAffiliations.getSubscriberAffiliation(jid);

			if (!this.config.isAdmin(JIDUtils.getNodeID(senderJid))
					&& (senderAffiliation.getAffiliation() != Affiliation.owner)
					&& !JIDUtils.getNodeID(jid).equals(JIDUtils.getNodeID(senderJid))) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.INVALID_JID);
			}
			if (affiliation != null) {
				if (affiliation.getAffiliation() == Affiliation.outcast) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
			}

			ISubscriptions nodeSubscriptions = this.repository.getNodeSubscriptions(toJid, nodeName);

			if (subid != null) {
				String s = nodeSubscriptions.getSubscriptionId(jid);

				if (!subid.equals(s)) {
					throw new PubSubException(element, Authorization.NOT_ACCEPTABLE, PubSubErrorCondition.INVALID_SUBID);
				}
			}

			Subscription subscription = nodeSubscriptions.getSubscription(jid);

			if (subscription == null) {
				throw new PubSubException(Authorization.UNEXPECTED_REQUEST, PubSubErrorCondition.NOT_SUBSCRIBED);
			}
			nodeSubscriptions.changeSubscription(jid, Subscription.none);
			if (nodeSubscriptions.isChanged()) {
				this.repository.update(toJid, nodeName, nodeSubscriptions);
			}

			return makeArray(packet.okResult((Element) null, 0));
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
