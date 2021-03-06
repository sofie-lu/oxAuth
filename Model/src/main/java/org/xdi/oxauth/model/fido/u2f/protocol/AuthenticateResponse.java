/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2015, Gluu
 */

package org.xdi.oxauth.model.fido.u2f.protocol;

import java.io.Serializable;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jboss.resteasy.annotations.providers.jaxb.IgnoreMediaTypes;
import org.xdi.oxauth.model.fido.u2f.exception.BadInputException;

/**
 * FIDO U2F device authentication response
 *
 * @author Yuriy Movchan Date: 05/13/2015
 */
@IgnoreMediaTypes("application/*+json")
// try to ignore jettison as it's recommended here:
// http://docs.jboss.org/resteasy/docs/2.3.4.Final/userguide/html/json.html
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticateResponse implements Serializable {

	private static final long serialVersionUID = -4854326288654670000L;

	/**
	 * base64(UTF8(client data))
	 */
	@JsonProperty
	private final String clientData;

	@JsonIgnore
	private transient ClientData clientDataRef;

	/* base64(raw response from U2F device) */
	@JsonProperty
	private final String signatureData;

	/* keyHandle originally passed */
	@JsonProperty
	private final String keyHandle;

	public AuthenticateResponse(@JsonProperty("clientData") String clientData, @JsonProperty("signatureData") String signatureData,
			@JsonProperty("keyHandle") String keyHandle) throws BadInputException {
		this.clientData = clientData;
		this.signatureData = signatureData;
		this.keyHandle = keyHandle;
		this.clientDataRef = new ClientData(clientData);
	}

	public ClientData getClientData() {
		return clientDataRef;
	}

	public String getSignatureData() {
		return signatureData;
	}

	public String getKeyHandle() {
		return keyHandle;
	}

	@JsonIgnore
	public String getRequestId() {
		return getClientData().getChallenge();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AuthenticateResponse [clientData=").append(clientData).append(", signatureData=").append(signatureData).append(", keyHandle=")
				.append(keyHandle).append("]");
		return builder.toString();
	}

}
