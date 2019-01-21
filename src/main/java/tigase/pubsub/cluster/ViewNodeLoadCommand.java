/**
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.pubsub.cluster;

import java.util.Map.Entry;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.xmpp.Authorization;

public class ViewNodeLoadCommand implements AdHocCommand {

	private final PubSubConfig config;

	private final ClusterNodeMap nodeMap;

	public ViewNodeLoadCommand(PubSubConfig config, ClusterNodeMap nodeMap) {
		this.config = config;
		this.nodeMap = nodeMap;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(request.getSender())) {
				throw new AdHocCommandException(Authorization.FORBIDDEN);
			}

			Form form = new Form("result", "Cluster nodes load", "Statistics of cluster nodes");

			for (Entry<String, Integer> entry : this.nodeMap.getClusterNodesLoad().entrySet()) {
				Field field = Field.fieldTextSingle("tigase#node-" + entry.getKey(), entry.getValue().toString(),
						entry.getKey());
				form.addField(field);

			}

			response.getElements().add(form.getElement());
			response.completeSession();

		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "View cluster load";
	}

	@Override
	public String getNode() {
		return "cluster-load";
	}

}
