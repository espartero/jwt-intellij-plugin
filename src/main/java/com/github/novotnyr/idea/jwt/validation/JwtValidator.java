package com.github.novotnyr.idea.jwt.validation;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.impl.PublicClaims;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.novotnyr.idea.jwt.SignatureContext;
import com.github.novotnyr.idea.jwt.core.AlgorithmResolver;
import com.github.novotnyr.idea.jwt.core.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JwtValidator {
    public final Logger logger = LoggerFactory.getLogger(getClass());

    private List<GlobalError> globalErrors = new ArrayList<>();

    private List<ClaimError> claimErrors = new ArrayList<>();

    public JwtValidator validateClaims(Jwt jwt) {
        DecodedJWT decodedJWT = JWT.decode(jwt.toString());
        return validateClaims(decodedJWT);
    }

    private JwtValidator validateClaims(DecodedJWT jwt) {
        assertInFuture(jwt, PublicClaims.EXPIRES_AT);
        assertInPast(jwt, PublicClaims.ISSUED_AT);
        assertInPast(jwt, PublicClaims.NOT_BEFORE);

        return this;
    }

    public void validate(Jwt jwt, SignatureContext signatureContext) {
        DecodedJWT decodedJWT = JWT.decode(jwt.toString());
        validate(decodedJWT, signatureContext);
    }

    private void validate(DecodedJWT jwt, SignatureContext signatureContext) {
        validateClaims(jwt);
        try {
            Algorithm algorithm = AlgorithmResolver.resolve(jwt.getAlgorithm(), signatureContext);
            algorithm.verify(jwt);
        } catch (SignatureVerificationException e) {
            globalErrors.add(new SignatureError());
        } catch (IllegalArgumentException e) {
            globalErrors.add(SignatureError.forEmptySecret());
        } catch (UnknownAlgorithmException e) {
            globalErrors.add(SignatureError.forUnknownAlgorithm(jwt.getAlgorithm()));
        }
    }

    public SignatureError getSignatureError() {
        for (GlobalError globalError : this.globalErrors) {
            if(globalError instanceof SignatureError) {
                return (SignatureError) globalError;
            }
        }
        return null;
    }

    public boolean hasSignatureError() {
        for (GlobalError globalError : this.globalErrors) {
            if(globalError instanceof SignatureError) {
                return true;
            }
        }
        return false;
    }

    private void assertInFuture(DecodedJWT jwt, String claimName) {
        Date date = jwt.getClaim(claimName).asDate();
        if(date == null) {
            this.claimErrors.add(new ClaimError(claimName, "Illegal type: not a date"));
            return;
        }
        if(!date.after(new Date())) {
            this.claimErrors.add(new ClaimError(claimName, "Future date expected"));
        }
    }

    private void assertInPast(DecodedJWT jwt, String claimName) {
        Date date = jwt.getClaim(claimName).asDate();
        if(date == null) {
            this.claimErrors.add(new ClaimError(claimName, "Illegal type: not a date"));
            return;
        }
        if(!date.before(new Date())) {
            this.claimErrors.add(new ClaimError(claimName, "Past date expected"));
        }
    }

    public List<ClaimError> getClaimErrors() {
        return claimErrors;
    }

    public List<GlobalError> getGlobalErrors() {
        return globalErrors;
    }
}
