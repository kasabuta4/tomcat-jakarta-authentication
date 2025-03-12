package com.github.kasabuta4.tomcat.jakarta.authentication.auth.module;

import java.io.IOException;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.realm.GenericPrincipal;

public class RequestValidator {

  private static final String APP_USER_ID_PARAMETER_NAME = "appUserId";

  private static final String STORED_PRINCIPAL_ATTRIBUTE_NAME
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.principal";
  private static final String CANDIDATES_ATTRIBUTE_NAME
      = "com.github.kasabuta4.tomcat.jakarta.authentication.auth.module.candidates";
  private static final List<String> UNAUTHENTICATED_USER_ROLES
      = Collections.unmodifiableList(Arrays.asList(AppUser.USER_ROLE_NAME));

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

  private Optional<AppUser> appUser;

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
      return invokeService(getStoredAuthenticatedPrincipal());
    }
    if (!isAuthenticatedExternally() || getCandidates().isEmpty()) {
      return sendFailure();
    }
    if (!isLoginRequest()) {
      return invokeService(createUnauthenticatedPrincipal());
    }
    if (!isAppUserSpecified() || !isAppUserIdentified()) {
      return sendFailure();
    }
    saveAuthenticatedPrincipal(createAuthenticatedPrincipal());
    return invokeService(getStoredAuthenticatedPrincipal());
  }

  private boolean isAlreadyAuthenticated() {
    return getStoredAuthenticatedPrincipal() != null;
  }

  private Principal getStoredAuthenticatedPrincipal() {
    return (Principal)request.getSession().getAttribute​(STORED_PRINCIPAL_ATTRIBUTE_NAME);
  }

  private AuthStatus invokeService(Principal principal) {
    try {
      handler.handle​(toCallbackArray(createCallerPrincipalCallback(principal)));
    } catch (IOException | UnsupportedCallbackException ex) {
      //  ignore
    }
    return AuthStatus.SUCCESS;
  }

  private Callback[] toCallbackArray(Callback callback) {
    return new Callback[] { callback };
  }

  private Callback createCallerPrincipalCallback(Principal principal) {
    return new CallerPrincipalCallback(clientSubject, principal);
  }

  private boolean isAuthenticatedExternally() {
    return getExternalUserId() != null;
  }

  private String getExternalUserId() {
    //return (String)request.getAttribute(externalUserIdAttributeName);
    return (String)request.getParameter(externalUserIdAttributeName);
  }

  private List<AppUser> getCandidates() throws AuthException {
    if (!isCandidatesStored()) {
      saveCandidates(loadCandidates());
    }
    return restoreCandidates();
  }

  private boolean isCandidatesStored() {
    return restoreCandidates() != null;
  }

  @SuppressWarnings("unchecked")
  private List<AppUser> restoreCandidates() {
    return (List<AppUser>)request.getSession().getAttribute​(CANDIDATES_ATTRIBUTE_NAME);
  }

  private void saveCandidates(List<AppUser> candidates) {
    request.getSession().setAttribute​(CANDIDATES_ATTRIBUTE_NAME, candidates);
  }

  private List<AppUser> loadCandidates() throws AuthException {
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

  private Principal createUnauthenticatedPrincipal() {
    return new GenericPrincipal(getExternalUserId(), UNAUTHENTICATED_USER_ROLES);
  }

  private boolean isAppUserSpecified() {
    return getAppUserId() != null;
  }

  private String getAppUserId() {
    return request.getParameter​(APP_USER_ID_PARAMETER_NAME);
  }

  private boolean isAppUserIdentified() throws AuthException {
    return getIdentifiedAppUser().isPresent();
  }

  private Optional<AppUser> getIdentifiedAppUser() throws AuthException {
    if (appUser == null) {
      appUser = identifyAppUser();
    }
    return appUser;
  }

  private Optional<AppUser> identifyAppUser() throws AuthException {
    String appUserId = getAppUserId();
    return getCandidates()
        .stream()
        .filter(candidate -> appUserId.equals(candidate.getAppUserId()))
        .findFirst();
  }

  private Principal createAuthenticatedPrincipal() throws AuthException {
    return new GenericPrincipal(
        getIdentifiedAppUser().get().getAppUserId(),
        getIdentifiedAppUser().get().getRoles()
    );
  }

  private void saveAuthenticatedPrincipal(Principal principal) {
    request.getSession().setAttribute(STORED_PRINCIPAL_ATTRIBUTE_NAME, principal);
  }
}
