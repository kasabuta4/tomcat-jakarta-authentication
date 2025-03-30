package com.github.kasabuta4.tomcat.jakarta.authentication.auth.module;

import java.io.Serializable;
import java.util.Map;

public class AppUser implements Serializable {

  public static final String USER_ROLE_NAME = "user";
  public static final String EMPLOYEE_ROLE_NAME = "employee";
  public static final String MANAGER_ROLE_NAME = "manager";
  public static final String ADMIN_ROLE_NAME = "admin";
  
  private static final Map<String, String[]> GROUP_CODE_TO_ROLES_MAP
      = Map.of(
          "1", new String[] {USER_ROLE_NAME, EMPLOYEE_ROLE_NAME},
          "2", new String[] {USER_ROLE_NAME, EMPLOYEE_ROLE_NAME, MANAGER_ROLE_NAME},
          "3", new String[] {USER_ROLE_NAME, ADMIN_ROLE_NAME}
      );

  private String appUserId;
  private String externalUserId;
  private String groupCode;

  public AppUser() {}

  public String getAppUserId() {
    return appUserId;
  }

  public void setAppUserId(String appUserId) {
    this.appUserId = appUserId;
  }

  public String getExternalUserId() {
    return externalUserId;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  public String getGroupCode() {
    return groupCode;
  }

  public void setGroupCode(String groupCode) {
    this.groupCode = groupCode;
  }

  public String[] getRoles() {
    return GROUP_CODE_TO_ROLES_MAP.get(groupCode);
  }
}
