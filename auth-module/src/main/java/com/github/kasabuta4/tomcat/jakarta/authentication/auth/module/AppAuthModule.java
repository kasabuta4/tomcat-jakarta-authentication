package com.github.kasabuta4.tomcat.jakarta.authentication.auth.module;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.MessagePolicy;
import jakarta.security.auth.message.module.ServerAuthModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class AppAuthModule implements ServerAuthModule {

  private static final Class<?>[] SUPPORTED_MESSAGE_TYPES
      = new Class[] { HttpServletRequest.class, HttpServletResponse.class };

  public static final String DATA_SOURCE_NAME_KEY
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.dataSource";
  public static final String EXTERNAL_USER_ID_ATTRIBUTE_NAME_KEY
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.externalUserId";
  public static final String LOGIN_PATH_KEY
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.loginPath";

  private static final String DEFAULT_EXTERNAL_USER_ID_ATTRIBUTE_NAME = "REMOTE_USER";
  private static final String DEFAULT_LOGIN_PATH = "/login.html";

  private CallbackHandler handler;
  private String dataSourceName;
  private String externalUserIdAttributeName;
  private String loginPath;

  @Override
  public Class<?>[] getSupportedMessageTypes() {
    return SUPPORTED_MESSAGE_TYPES;
  }

  @Override
  public void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
      CallbackHandler handler, Map<String,Object> options) throws AuthException {
    this.handler = handler;
    this.dataSourceName = (String)options.get(DATA_SOURCE_NAME_KEY);
    this.externalUserIdAttributeName
        = (String)options.getOrDefault(
            EXTERNAL_USER_ID_ATTRIBUTE_NAME_KEY, DEFAULT_EXTERNAL_USER_ID_ATTRIBUTE_NAME
        );
    this.loginPath = (String)options.getOrDefault(LOGIN_PATH_KEY, DEFAULT_LOGIN_PATH);
  }

  @Override
  public AuthStatus validateRequest(
      MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject
  ) throws AuthException {
    return new RequestValidator(
        handler, messageInfo, clientSubject,
        dataSourceName, externalUserIdAttributeName, loginPath
    ).validate();
  }
}
