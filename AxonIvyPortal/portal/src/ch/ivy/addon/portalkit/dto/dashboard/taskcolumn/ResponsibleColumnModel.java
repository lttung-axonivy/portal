package ch.ivy.addon.portalkit.dto.dashboard.taskcolumn;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.axonivy.portal.components.dto.SecurityMemberDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.ivy.addon.portalkit.constant.PortalConstants;
import ch.ivy.addon.portalkit.enums.DashboardColumnFormat;
import ch.ivy.addon.portalkit.enums.DashboardStandardTaskColumn;
import ch.ivy.addon.portalkit.ivydata.utils.ServiceUtilities;
import ch.ivy.addon.portalkit.util.SecurityMemberDisplayNameUtils;
import ch.ivy.addon.portalkit.util.SecurityMemberUtils;
import ch.ivyteam.ivy.security.ISecurityMember;
import ch.ivyteam.ivy.workflow.ITask;

public class ResponsibleColumnModel extends TaskColumnModel implements Serializable {

  private static final long serialVersionUID = -4315469062114036720L;

  @Override
  public void initDefaultValue() {
    super.initDefaultValue();
    this.field = DashboardStandardTaskColumn.RESPONSIBLE.getField();
    this.styleToDisplay = initDefaultStyle();
    this.format = getDefaultFormat();
    this.styleClass = defaultIfEmpty(this.styleClass, getDefaultStyleClass());
    this.quickSearch = defaultIfEmpty(this.quickSearch, false);
  }

  @Override
  public String getDefaultHeaderCMS() {
    return "/ch.ivy.addon.portalkit.ui.jsf/taskList/defaultColumns/ACTIVATOR";
  }

  @Override
  public DashboardColumnFormat getDefaultFormat() {
    return DashboardColumnFormat.CUSTOM;
  }

  @Override
  protected int getDefaultColumnWidth() {
    return EXTRA_WIDTH;
  }

  @Override
  public String getDefaultStyleClass() {
    return "dashboard-tasks__responsible widget-column";
  }

  @Override
  public Object display(ITask task) {
    if (task == null || CollectionUtils.isEmpty(task.responsibles().all())) {
      return StringUtils.EMPTY;
    }
    ISecurityMember member = task.responsibles().all().getFirst().get();
    return SecurityMemberDisplayNameUtils.generateBriefDisplayNameForSecurityMember(member, member.getMemberName());
  }
  
  @JsonIgnore
  public List<SecurityMemberDTO> getResponsibles() {
    return this.filterList.stream().map(this::findSecurityMember).collect(Collectors.toList());
  }
  
  @JsonIgnore
  public void setResponsibles(List<SecurityMemberDTO> responsibles) {
    if (responsibles != null) {
      this.filterList = responsibles.stream().map(SecurityMemberDTO::getMemberName).collect(Collectors.toList());
    } else {
      this.filterList = new ArrayList<>();
    }
  }
  
  @JsonIgnore
  public List<SecurityMemberDTO> getUserFilterResponsibles() {
    return this.userFilterList.stream().map(this::findSecurityMember).collect(Collectors.toList());
  }
  
  @JsonIgnore
  public void setUserFilterResponsibles(List<SecurityMemberDTO> responsibles) {
    if (responsibles != null) {
      this.userFilterList = responsibles.stream().map(SecurityMemberDTO::getMemberName).collect(Collectors.toList());
    } else {
      this.userFilterList = new ArrayList<>();
    }
  }
  
  @JsonIgnore
  public int getResponsibleSize(ITask task) {
    if (task == null || CollectionUtils.isEmpty(task.responsibles().all())) {
      return 0;
    }
    return task.responsibles().all().size();
  }
  
  @Override
  public Boolean getDefaultSortable() {
    return false;
  }
  
  public List<SecurityMemberDTO> completeUserFilterResponsibles(String query) {
    List<SecurityMemberDTO> options = toSecurityMemberDTOs(getFilterList(), query);
    if (CollectionUtils.isEmpty(options)) {
      options = SecurityMemberUtils.findSecurityMembers(query, 0, PortalConstants.MAX_USERS_IN_AUTOCOMPLETE);
    }
    return options;
  }
  
  private List<SecurityMemberDTO> toSecurityMemberDTOs(List<String> filters, String query) {
    if (CollectionUtils.isEmpty(filters)) {
      return new ArrayList<>();
    }
    return filters.stream()
        .map(this::findSecurityMember)
        .filter(m -> StringUtils.containsIgnoreCase(m.getDisplayName(), query) || StringUtils.containsIgnoreCase(m.getName(), query))
        .collect(Collectors.toList());
  }
  
  private SecurityMemberDTO findSecurityMember(String memberName) {
    return ServiceUtilities.findSecurityMemberByName(memberName);
  }

  @Override
  public boolean canQuickSearch() {
    return true;
  }
}
