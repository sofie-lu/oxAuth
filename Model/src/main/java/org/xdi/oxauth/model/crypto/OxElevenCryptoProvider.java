/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.crypto;

import org.apache.commons.httpclient.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.gluu.oxeleven.client.*;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;

/**
 * @author Javier Rojas Blum
 * @version June 15, 2016
 */
public class OxElevenCryptoProvider extends AbstractCryptoProvider {

    private String generateKeyEndpoint;
    private String signEndpoint;
    private String verifySignatureEndpoint;
    private String deleteKeyEndpoint;

    public OxElevenCryptoProvider(String generateKeyEndpoint, String signEndpoint, String verifySignatureEndpoint, String deleteKeyEndpoint) {
        this.generateKeyEndpoint = generateKeyEndpoint;
        this.signEndpoint = signEndpoint;
        this.verifySignatureEndpoint = verifySignatureEndpoint;
        this.deleteKeyEndpoint = deleteKeyEndpoint;
    }

    @Override
    public JSONObject generateKey(SignatureAlgorithm signatureAlgorithm, Long expirationTime) throws Exception {
        GenerateKeyRequest request = new GenerateKeyRequest();
        request.setSignatureAlgorithm(signatureAlgorithm.getName());
        request.setExpirationTime(expirationTime);

        GenerateKeyClient client = new GenerateKeyClient(generateKeyEndpoint);
        client.setRequest(request);

        GenerateKeyResponse response = client.exec();
        if (response.getStatus() == HttpStatus.SC_OK && response.getKeyId() != null) {
            return response.getJSONEntity();
        } else {
            throw new Exception(response.getEntity());
        }
    }

    @Override
    public String sign(String signingInput, String keyId, String shardSecret, SignatureAlgorithm signatureAlgorithm) throws Exception {
        SignRequest request = new SignRequest();
        request.getSignRequestParam().setSigningInput(signingInput);
        request.getSignRequestParam().setAlias(keyId);
        request.getSignRequestParam().setSharedSecret(shardSecret);
        request.getSignRequestParam().setSignatureAlgorithm(signatureAlgorithm.getName());

        SignClient client = new SignClient(signEndpoint);
        client.setRequest(request);

        SignResponse response = client.exec();
        if (response.getStatus() == HttpStatus.SC_OK && response.getSignature() != null) {
            return response.getSignature();
        } else {
            throw new Exception(response.getEntity());
        }
    }

    @Override
    public boolean verifySignature(String signingInput, String encodedSignature, String keyId, JSONObject jwks, String sharedSecret, SignatureAlgorithm signatureAlgorithm) throws Exception {
        VerifySignatureRequest request = new VerifySignatureRequest();
        request.getVerifySignatureRequestParam().setSigningInput(signingInput);
        request.getVerifySignatureRequestParam().setSignature(encodedSignature);
        request.getVerifySignatureRequestParam().setAlias(keyId);
        request.getVerifySignatureRequestParam().setSharedSecret(sharedSecret);
        request.getVerifySignatureRequestParam().setSignatureAlgorithm(signatureAlgorithm.getName());
        if (jwks != null) {
            request.getVerifySignatureRequestParam().setJwksRequestParam(getJwksRequestParam(jwks));
        }

        VerifySignatureClient client = new VerifySignatureClient(verifySignatureEndpoint);
        client.setRequest(request);

        VerifySignatureResponse response = client.exec();
        if (response.getStatus() == HttpStatus.SC_OK) {
            return response.isVerified();
        } else {
            throw new Exception(response.getEntity());
        }
    }

    @Override
    public boolean deleteKey(String keyId) throws Exception {
        DeleteKeyRequest request = new DeleteKeyRequest();
        request.setAlias(keyId);

        DeleteKeyClient client = new DeleteKeyClient(deleteKeyEndpoint);
        client.setRequest(request);

        DeleteKeyResponse response = client.exec();
        if (response.getStatus() == org.apache.http.HttpStatus.SC_OK) {
            return response.isDeleted();
        } else {
            throw new Exception(response.getEntity());
        }
    }
}
