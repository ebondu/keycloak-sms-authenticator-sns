package org.keycloak.action.required;

import org.keycloak.sms.KeycloakSmsConstants;
import org.keycloak.sms.KeycloakSmsSenderService;
import org.keycloak.sms.impl.KeycloakSmsUtil;
import org.jboss.logging.Logger;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


/**
 * Created by nickpack on 15/08/2017.
 */
public class KeycloakSmsMobilenumberValidationRequiredAction implements RequiredActionProvider {
    private static Logger logger = Logger.getLogger(KeycloakSmsMobilenumberValidationRequiredAction.class);
    public static final String PROVIDER_ID = "sms_auth_check_mobile_validation";

    public void evaluateTriggers(RequiredActionContext context) {
        logger.debug("evaluateTriggers called ...");
    }

    public void requiredActionChallenge(RequiredActionContext context) {
        logger.debug("requiredActionChallenge called ...");

        UserModel user = context.getUser();

        List<String> mobileNumberCreds = user.getAttribute(KeycloakSmsConstants.ATTR_MOBILE);
        List<String> mobileNumberVerifiedCreds = user.getAttribute(KeycloakSmsConstants.ATTR_MOBILE_VERIFIED);

        String mobileNumber = null;
        String mobileNumberValidation = null;

        if (mobileNumberCreds != null && !mobileNumberCreds.isEmpty()) {
            mobileNumber = mobileNumberCreds.get(0);
        }
        if (mobileNumberVerifiedCreds != null && !mobileNumberVerifiedCreds.isEmpty()) {
            mobileNumberValidation = mobileNumberVerifiedCreds.get(0);
        }

        Theme theme = null;
        Locale locale = context.getSession().getContext().resolveLocale(context.getUser());
        try {
            theme = context.getSession().theme().getTheme(context.getRealm().getLoginTheme(), Theme.Type.LOGIN);
        } catch (Exception e) {
            logger.error("Unable to get theme required to send SMS", e);
        }
        if (mobileNumberValidation != null && KeycloakSmsUtil.validateTelephoneNumber(mobileNumberValidation, KeycloakSmsUtil.getMessage(theme, locale, KeycloakSmsConstants.MSG_MOBILE_REGEXP))
                && mobileNumber != null && KeycloakSmsUtil.validateTelephoneNumber(mobileNumber, KeycloakSmsUtil.getMessage(theme, locale, KeycloakSmsConstants.MSG_MOBILE_REGEXP))) {
            // Mobile number is configured and validated
            context.ignore();
        } else if (mobileNumberValidation == null){
            logger.debug("SMS validation required ...");

            KeycloakSmsSenderService provider = context.getSession().getProvider(KeycloakSmsSenderService.class);
            if (provider.sendSmsCode(mobileNumber, context)) {
                Response challenge = context.form()
                        .setAttribute("mobile_number", user.getAttributes().get("mobile_number").get(0))
                        .createForm("sms-validation.ftl");

                context.challenge(challenge);
            } else {
                Response challenge = context.form()
                        .setError("sms-auth.not.send")
                        .createForm("sms-validation-error.ftl");
                context.challenge(challenge);
            }
        }
    }

    public void processAction(RequiredActionContext context) {
        logger.debug("action called ... context = " + context);

        KeycloakSmsSenderService provider = context.getSession().getProvider(KeycloakSmsSenderService.class);
        KeycloakSmsSenderService.CODE_STATUS status = provider.validateCode(context);
        Response challenge;

        switch (status) {
            case EXPIRED:
                challenge = context.form()
                        .setError("sms-auth.code.expired")
                        .createForm("sms-validation.ftl");
                context.challenge(challenge);
                break;

            case INVALID:
                challenge = context.form()
                        .setError("sms-auth.code.invalid")
                        .createForm("sms-validation.ftl");
                context.challenge(challenge);
                break;

            case VALID:
                context.success();
                provider.updateVerifiedMobilenumber(context.getUser());
                break;
        }
    }

    public void close() {
        logger.debug("close called ...");
    }
}
