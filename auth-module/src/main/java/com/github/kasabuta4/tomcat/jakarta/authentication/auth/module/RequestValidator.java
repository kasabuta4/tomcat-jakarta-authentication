package com.github.kasabuta4.tomcat.jakarta.authentication.auth.module;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.sql.DataSource;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.callback.CallerPrincipalCallback;
import jakarta.security.auth.message.callback.GroupPrincipalCallback;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequestValidator {

  private static final String APP_USER_ID_PARAMETER_NAME = "appUserId";

  private static final String APP_USER_ATTRIBUTE_NAME
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.appUser";
  private static final String CANDIDATES_ATTRIBUTE_NAME
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.candidates";
  private static final String[] UNAUTHENTICATED_USER_ROLES
      = new String[] {AppUser.USER_ROLE_NAME};

  private static final String JDBC_RESOURCE_CONTEXT_URI = "java:comp/env/jdbc";
  private static final String SELECT_CANDIDATES_SQL_STATEMENT
      = "SELECT APP_USER_ID, EXTERNAL_USER_ID, APP_GROUP FROM APP_USER "
      + "WHERE EXTERNAL_USER_ID = ? ORDER BY APP_USER_ID";

  private static final Logger logger = Logger.getLogger(RequestValidator.class.getName());

  private final CallbackHandler handler;
  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final Subject clientSubject;
  private final String dataSourceName;
  private final String externalUserIdAttributeName;
  private final String loginPath;

  public RequestValidator(
      CallbackHandler handler, MessageInfo messageInfo, Subject clientSubject,
      String dataSourceName, String externalUserIdAttributeName, String loginPath
  ) {
    this.handler = handler;
    this.request = (HttpServletRequest)messageInfo.getRequestMessage();
    this.response = (HttpServletResponse)messageInfo.getResponseMessage();
    this.clientSubject = clientSubject;
    this.dataSourceName = dataSourceName;
    this.externalUserIdAttributeName = externalUserIdAttributeName;
    this.loginPath = loginPath;
  }

  public AuthStatus validate() throws AuthException {
    if (isAlreadyAuthenticated()) {
      return invokeService(createAppUserCallbacks());
    }
    if (!isAuthenticatedExternally() || getCandidates().isEmpty()) {
      return sendFailure();
    }
    if (!isLoginRequest()) {
      return invokeService(createAnonymousCallbacks());
    }
    if (!isAppUserSpecified() || !isAppUserIdentifiable()) {
      return sendFailure();
    }
    return invokeService(createAppUserCallbacks());
  }

  private boolean isAlreadyAuthenticated() {
    return getAppUserFromSession() != null;
  }

  private AppUser getAppUserFromSession() {
    return (AppUser)request.getSession().getAttribute(APP_USER_ATTRIBUTE_NAME);
  }

  private AuthStatus invokeService(Callback[] callbacks) {
    try {
      handler.handle​(callbacks);
    } catch (IOException | UnsupportedCallbackException ex) {
      //  ignore
    }
    return AuthStatus.SUCCESS;
  }

  private Callback[] createAppUserCallbacks() {
    AppUser appUser = getAppUserFromSession();
    return createCallbacks(appUser.getAppUserId(), appUser.getRoles());
  }

  private Callback[] createCallbacks(String name, String[] groups) {
    return new Callback[] {
        new CallerPrincipalCallback(clientSubject, name),
        new GroupPrincipalCallback(clientSubject, groups)
    };
  }

  private boolean isAuthenticatedExternally() {
    return getExternalUserId() != null;
  }

  private String getExternalUserId() {
    //return (String)request.getAttribute(externalUserIdAttributeName);
    return request.getParameter(externalUserIdAttributeName);
  }

  private List<AppUser> getCandidates() throws AuthException {
    if (!isCandidatesStored()) {
      saveCandidatesToSession(getCandidatesFromDataSource());
    }
    return getCandidatesFromSession();
  }

  private boolean isCandidatesStored() {
    return getCandidatesFromSession() != null;
  }

  @SuppressWarnings("unchecked")
  private List<AppUser> getCandidatesFromSession() {
    return (List<AppUser>)request.getSession().getAttribute​(CANDIDATES_ATTRIBUTE_NAME);
  }

  private void saveCandidatesToSession(List<AppUser> candidates) {
    request.getSession().setAttribute​(CANDIDATES_ATTRIBUTE_NAME, candidates);
  }

  private List<AppUser> getCandidatesFromDataSource() throws AuthException {
    List<AppUser> appUsers = new ArrayList<>();
    try {
      try (Connection connection = getConnection()) {
        try (ResultSet resultSet = getResultSet(connection)) {
          while (resultSet.next()) {
            appUsers.add(convertToAppUser(resultSet));
          }
        }
      }
    } catch (NamingException | SQLException ex) {
      logger.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
      throw new AuthException(ex);
    }
    return appUsers;
  }

  private Connection getConnection() throws NamingException, SQLException {
    InitialContext initialContext = new InitialContext();
    Context context = (Context)initialContext.lookup(JDBC_RESOURCE_CONTEXT_URI);
    DataSource dataSource = (DataSource)context.lookup(dataSourceName);
    return dataSource.getConnection();
  }

  private ResultSet getResultSet(Connection connection) throws SQLException {
    PreparedStatement statement = connection.prepareStatement​(SELECT_CANDIDATES_SQL_STATEMENT);
    statement.setString(1, getExternalUserId());
    return statement.executeQuery();
  }

  private AppUser convertToAppUser(ResultSet resultSet) throws SQLException {
    AppUser user = new AppUser();
    user.setAppUserId(resultSet.getString​(1));
    user.setExternalUserId(resultSet.getString​(2));
    user.setGroupCode(resultSet.getString​(3));
    return user;
  }

  private AuthStatus sendFailure() {
    response.setStatus​(HttpServletResponse.SC_FORBIDDEN);
    return AuthStatus.SEND_FAILURE;
  }

  private boolean isLoginRequest() {
    return loginPath.equals(getContextRelativePath());
  }

  private String getContextRelativePath() {
    return request.getPathInfo() == null
        ? request.getServletPath()
        : request.getServletPath() + request.getPathInfo();
  }

  private Callback[] createAnonymousCallbacks() {
    return createCallbacks(getExternalUserId(), UNAUTHENTICATED_USER_ROLES);
  }

  private boolean isAppUserSpecified() {
    return getAppUserId() != null;
  }

  private String getAppUserId() {
    return request.getParameter​(APP_USER_ID_PARAMETER_NAME);
  }

  private boolean isAppUserIdentifiable() throws AuthException {
    Optional<AppUser> identifiedAppUser = identifyAppUser();
    if (identifiedAppUser.isPresent()) {
      saveAppUserToSession(identifiedAppUser.get());
    }
    return identifiedAppUser.isPresent();
  }

  private Optional<AppUser> identifyAppUser() throws AuthException {
    String specifiedAppUserId = getAppUserId();
    return getCandidates()
        .stream()
        .filter(candidate -> Objects.equals(candidate.getAppUserId(), specifiedAppUserId))
        .findFirst();
  }

  private void saveAppUserToSession(AppUser appUser) {
    request.getSession().setAttribute(APP_USER_ATTRIBUTE_NAME, appUser);
  }
}
