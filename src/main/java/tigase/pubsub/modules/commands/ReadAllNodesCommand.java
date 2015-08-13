package tigase.pubsub.modules.commands;

import java.util.Arrays;

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.component.exceptions.RepositoryException;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

@Bean(name = "readAllNodesCommand")
public class ReadAllNodesCommand implements AdHocCommand {

	@Inject
	private PubSubConfig config;
	@Inject
	private IPubSubDAO<?> dao;
	@Inject
	private IPubSubRepository repository;

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.getAction() != null && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else if (data == null) {
				Form form = new Form("result", "Reading all nodes", "To read all nodes from DB press finish");

				response.getElements().add(form.getElement());
				response.startSession();

			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					startReading(request.getIq().getStanzaTo().getBareJID());
					Form f = new Form(null, "Info", "Nodes tree has been readed");
					response.getElements().add(f.getElement());
				}
				response.completeSession();
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Read ALL nodes";
	}

	@Override
	public String getNode() {
		return "read-all-nodes";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

	private void startReading(BareJID serviceJid) throws RepositoryException {
		final String[] allNodesId = dao.getAllNodesList(serviceJid);
		for (String n : allNodesId) {
			repository.getNodeConfig(serviceJid, n);
		}
	}

}
