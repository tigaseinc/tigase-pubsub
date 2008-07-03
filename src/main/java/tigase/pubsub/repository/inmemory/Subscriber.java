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
package tigase.pubsub.repository.inmemory;

import tigase.pubsub.Affiliation;
import tigase.pubsub.Subscription;

public class Subscriber {

	private final String jid;

	private Affiliation affiliation;

	private final String subid;

	private Subscription subscription;

	Subscriber(String jid, String subid, Affiliation affiliation, Subscription subscriptionType) {
		super();
		this.jid = jid;
		this.subid = subid;
		this.affiliation = affiliation;
		this.subscription = subscriptionType;
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}

	public String getJid() {
		return jid;
	}

	public String getSubid() {
		return subid;
	}

	public Subscription getSubscription() {
		return subscription;
	}

	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}

	public void setSubscription(Subscription subscriptionType) {
		this.subscription = subscriptionType;
	}

}
