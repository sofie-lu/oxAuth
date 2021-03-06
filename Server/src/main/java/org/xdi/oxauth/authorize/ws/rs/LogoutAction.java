/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.authorize.ws.rs;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.gluu.jsf2.service.FacesService;
import org.slf4j.Logger;
import org.xdi.model.custom.script.conf.CustomScriptConfiguration;
import org.xdi.oxauth.i18n.LanguageBean;
import org.xdi.oxauth.model.common.AuthorizationGrant;
import org.xdi.oxauth.model.common.AuthorizationGrantList;
import org.xdi.oxauth.model.common.SessionState;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.session.EndSessionRequestParam;
import org.xdi.oxauth.model.util.Base64Util;
import org.xdi.oxauth.model.util.Util;
import org.xdi.oxauth.service.SessionStateService;
import org.xdi.oxauth.service.external.ExternalAuthenticationService;
import org.xdi.service.JsonService;
import org.xdi.util.StringHelper;

import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Map;

/**
 * @author Javier Rojas Blum Date: 03.13.2012
 * @author Yuriy Movchan Date: 09/01/2016
 */
@RequestScoped
@Named
public class LogoutAction {

	private static final String EXTERNAL_LOGOUT = "external_logout";
	private static final String EXTERNAL_LOGOUT_DATA = "external_logout_data";

    @Inject
    private Logger log;

    @Inject
    private AuthorizationGrantList authorizationGrantList;
    
    @Inject
    private SessionStateService sessionStateService;

    @Inject
    private ExternalAuthenticationService externalAuthenticationService;

    @Inject
    private JsonService jsonService;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private FacesService facesService;

    @Inject
    private FacesContext facesContext;

	@Inject
	private LanguageBean languageBean;

    private String idTokenHint;
    private String postLogoutRedirectUri;
	private SessionState sessionState;


    public String getIdTokenHint() {
		return idTokenHint;
	}

	public void setIdTokenHint(String idTokenHint) {
		this.idTokenHint = idTokenHint;
	}

	public String getPostLogoutRedirectUri() {
		return postLogoutRedirectUri;
	}

	public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
		this.postLogoutRedirectUri = postLogoutRedirectUri;
	}

    public void redirect() {
		SessionState sessionState = sessionStateService.getSessionState();

		boolean validationResult = validateParameters(); 
    	if (!validationResult) {
    		try {
				restoreLogoutParametersFromSession(sessionState);
			} catch (IOException ex) {
	        	logoutFailed();
				log.debug("Failed to restore logout parameters from session", ex);
			}

    		validationResult = validateParameters(); 
    		if (!validationResult) {
        		missingLogoutParameters();
        		return;
    		}
    	}

        ExternalLogoutResult externalLogoutResult = processExternalAuthenticatorLogOut(sessionState);
        if (ExternalLogoutResult.FAILURE == externalLogoutResult) {
        	logoutFailed();
        	return;
        } else if (ExternalLogoutResult.REDIRECT == externalLogoutResult) {
        	return;
        }

        StringBuilder sb = new StringBuilder();

        // Required parameters
        if(idTokenHint!=null && !idTokenHint.isEmpty()){
            sb.append(EndSessionRequestParam.ID_TOKEN_HINT + "=").append(idTokenHint);
        }

        if (sessionState != null && !postLogoutRedirectUri.isEmpty()) {
            sb.append("&"+EndSessionRequestParam.SESSION_STATE+"=").append(sessionState.getId());
        }

        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isEmpty()) {
            sb.append("&"+EndSessionRequestParam.POST_LOGOUT_REDIRECT_URI+"=").append(postLogoutRedirectUri);
        }
        
        facesService.redirectToExternalURL("seam/resource/restv1/oxauth/end_session?" + sb.toString());
    }

	private boolean validateParameters() {
		return (StringHelper.isNotEmpty(idTokenHint) || (sessionState != null)) && StringHelper.isNotEmpty(postLogoutRedirectUri);
	}

	private ExternalLogoutResult processExternalAuthenticatorLogOut(SessionState sessionState) {
        if ((sessionState != null) && sessionState.getSessionAttributes().containsKey(EXTERNAL_LOGOUT)) {
			log.debug("Detected callback from external system. Resuming logout.");
			return ExternalLogoutResult.SUCCESS;
        }

        AuthorizationGrant authorizationGrant = authorizationGrantList.getAuthorizationGrantByIdToken(idTokenHint);
        if (authorizationGrant == null) {
        	Boolean endSessionWithAccessToken = appConfiguration.getEndSessionWithAccessToken();
        	if ((endSessionWithAccessToken != null) && endSessionWithAccessToken) {
        		authorizationGrant = authorizationGrantList.getAuthorizationGrantByAccessToken(idTokenHint);
        	}
        }
		if ((authorizationGrant == null) && (sessionState == null)) {
			return ExternalLogoutResult.FAILURE;
		}

		String acrValues;
		if (authorizationGrant == null) {
			acrValues = sessionStateService.getAcr(sessionState);
		} else {
			acrValues = authorizationGrant.getAcrValues();
		}

		boolean isExternalAuthenticatorLogoutPresent = StringHelper.isNotEmpty(acrValues);
		if (isExternalAuthenticatorLogoutPresent) {
			log.debug("Attemptinmg to execute logout method of '{}' external authenticator.", acrValues);

			CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService.getCustomScriptConfigurationByName(acrValues);
			if (customScriptConfiguration == null) {
				log.error("Failed to get ExternalAuthenticatorConfiguration. acr_values: {}", acrValues);
				return ExternalLogoutResult.FAILURE;
			} else {
				boolean scriptExternalLogoutResult = externalAuthenticationService.executeExternalLogout(customScriptConfiguration, null);
				ExternalLogoutResult externalLogoutResult = scriptExternalLogoutResult ? ExternalLogoutResult.SUCCESS : ExternalLogoutResult.FAILURE;
				log.debug("Logout result is '{}' for session '{}', userDn: '{}'", externalLogoutResult, sessionState.getId(), sessionState.getUserDn());					

				int apiVersion = externalAuthenticationService.executeExternalGetApiVersion(customScriptConfiguration);
	            if (apiVersion < 3) {
	            	// Not support redirect to external system at logout
					return externalLogoutResult;
	            }
	            	
            	log.trace("According to API version script supports logout redirects");
            	String logoutExternalUrl = externalAuthenticationService.getLogoutExternalUrl(customScriptConfiguration, null);
            	log.debug("External logout result is '{}' for user '{}'", logoutExternalUrl, sessionState.getUserDn());
				
				if (StringHelper.isEmpty(logoutExternalUrl)) {
					return externalLogoutResult;
				}

				// Store in session parameters needed to call end_session
				try {
					storeLogoutParametersInSession(sessionState);
				} catch (IOException ex) {
					log.debug("Failed to persist logout parameters in session", ex);

					return ExternalLogoutResult.FAILURE;
				}
				
				// Redirect to external URL
				facesService.redirectToExternalURL(logoutExternalUrl);
				return ExternalLogoutResult.REDIRECT;
			}
		} else {
			return ExternalLogoutResult.SUCCESS;
		}
	}

	private void storeLogoutParametersInSession(SessionState sessionState) throws JsonGenerationException, JsonMappingException, IOException {
		Map<String, String> sessionAttributes = sessionState.getSessionAttributes(); 
		
		LogoutParameters logoutParameters = new LogoutParameters(idTokenHint, postLogoutRedirectUri);

		String logoutParametersJson = jsonService.objectToJson(logoutParameters);
		String logoutParametersBase64 = Base64Util.base64urlencode(logoutParametersJson.getBytes(Util.UTF8_STRING_ENCODING));

		sessionAttributes.put(EXTERNAL_LOGOUT, Boolean.toString(true));
		sessionAttributes.put(EXTERNAL_LOGOUT_DATA, logoutParametersBase64);
		
		sessionStateService.updateSessionState(sessionState);
	}

	private boolean restoreLogoutParametersFromSession(SessionState sessionState) throws IllegalArgumentException, JsonParseException, JsonMappingException, IOException {
		if (sessionState == null) {
			return false;
		}

		this.sessionState = sessionState;
		Map<String, String> sessionAttributes = sessionState.getSessionAttributes(); 

		boolean restoreParameters = sessionAttributes.containsKey(EXTERNAL_LOGOUT);
		if (!restoreParameters) {
			return false;
		}

		String logoutParametersBase64 = sessionAttributes.get(EXTERNAL_LOGOUT_DATA);
		String logoutParametersJson = new String(Base64Util.base64urldecode(logoutParametersBase64), Util.UTF8_STRING_ENCODING);

		LogoutParameters logoutParameters = jsonService.jsonToObject(logoutParametersJson, LogoutParameters.class);
		
		this.idTokenHint = logoutParameters.getIdTokenHint();
		this.postLogoutRedirectUri = logoutParameters.getPostLogoutRedirectUri();
		
		return true;
	}

	public void missingLogoutParameters() {
		String message = languageBean.getValue("logout.missingParameters");
        facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, message, message));
		facesService.redirect("/error.xhtml");
	}

	public void logoutFailed() {
		String message = languageBean.getValue("logout.failedToProceed");
        facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, message, message));
		facesService.redirect("/error.xhtml");
	}
	
	public static class LogoutParameters {
		private String idTokenHint;
		private String postLogoutRedirectUri;

		public LogoutParameters() {}

		public LogoutParameters(String idTokenHint, String postLogoutRedirectUri) {
			this.idTokenHint = idTokenHint;
			this.postLogoutRedirectUri = postLogoutRedirectUri;
		}

		public String getIdTokenHint() {
			return idTokenHint;
		}

		public void setIdTokenHint(String idTokenHint) {
			this.idTokenHint = idTokenHint;
		}

		public String getPostLogoutRedirectUri() {
			return postLogoutRedirectUri;
		}

		public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
			this.postLogoutRedirectUri = postLogoutRedirectUri;
		}

	}

	private enum ExternalLogoutResult {
		SUCCESS,
		FAILURE,
		REDIRECT
	}

}