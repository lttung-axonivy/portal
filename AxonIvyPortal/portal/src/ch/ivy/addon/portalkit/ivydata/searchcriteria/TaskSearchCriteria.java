package ch.ivy.addon.portalkit.ivydata.searchcriteria;

import static ch.ivy.addon.portalkit.constant.CustomFields.CUSTOM_TIMESTAMP_FIELD5;
import static ch.ivyteam.ivy.workflow.TaskState.CREATED;
import static ch.ivyteam.ivy.workflow.TaskState.DELAYED;
import static ch.ivyteam.ivy.workflow.TaskState.DESTROYED;
import static ch.ivyteam.ivy.workflow.TaskState.DONE;
import static ch.ivyteam.ivy.workflow.TaskState.PARKED;
import static ch.ivyteam.ivy.workflow.TaskState.READY_FOR_JOIN;
import static ch.ivyteam.ivy.workflow.TaskState.RESUMED;
import static ch.ivyteam.ivy.workflow.TaskState.SUSPENDED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.axonivy.portal.enums.SearchScopeTaskField;

import ch.ivy.addon.portalkit.enums.TaskAssigneeType;
import ch.ivy.addon.portalkit.enums.TaskSortField;
import ch.ivyteam.ivy.workflow.TaskState;
import ch.ivyteam.ivy.workflow.query.TaskQuery;
import ch.ivyteam.ivy.workflow.query.TaskQuery.IFilterQuery;
import ch.ivyteam.ivy.workflow.task.TaskBusinessState;

public class TaskSearchCriteria {

  public final static List<TaskState> STANDARD_STATES = Arrays.asList(CREATED, SUSPENDED, RESUMED, PARKED, READY_FOR_JOIN);
  public final static List<TaskState> ADVANCE_STATES = Arrays.asList(DONE, DELAYED, DESTROYED, TaskState.JOIN_FAILED, TaskState.FAILED, TaskState.WAITING_FOR_INTERMEDIATE_EVENT);

  public final static List<TaskBusinessState> STANDARD_BUSINESS_STATES = Arrays.asList(TaskBusinessState.OPEN,
      TaskBusinessState.IN_PROGRESS);
  public final static List<TaskBusinessState> ADVANCE_BUSINESS_STATES = Arrays.asList(TaskBusinessState.OPEN,
      TaskBusinessState.IN_PROGRESS, TaskBusinessState.DONE, TaskBusinessState.DESTROYED, TaskBusinessState.DELAYED,
      TaskBusinessState.ERROR);

  private List<TaskState> includedStates;
  private boolean isAdminQuery;
  private String keyword;
  private Long taskId;
  private Long caseId;
  private String category;
  private TaskAssigneeType taskAssigneeType;
  private boolean isNewQueryCreated;
  private boolean isQueryByTaskId;
  private boolean isQueryByBusinessCaseId;
  private String sortField;
  private boolean sortDescending;
  private boolean isSorted = true;
  private TaskQuery customTaskQuery;
  private boolean isOnlyShowOpenTask;

  private List<SearchScopeTaskField> searchScopeTaskFields;
  private boolean isGlobalSearch;
  private boolean isGlobalSearchScope;

  @SuppressWarnings("deprecation")
  public TaskQuery createQueryToFindLatestTasks(TaskQuery taskQuery, Date timeStamp) {
    if (isAdminQuery) {
      taskQuery.where().and(TaskQuery.create().where().startTimestamp().isGreaterThan(timeStamp));
    } else {
      // task when delegate or reset, delegate time will write to customTimestampField5
      taskQuery.where().and(TaskQuery.create().where().startTimestamp().isGreaterThan(timeStamp).or().customField().timestampField(CUSTOM_TIMESTAMP_FIELD5)
          .isGreaterOrEqualThan(timeStamp));
    }
    return taskQuery;
  }

  public TaskQuery createQuery() {
    TaskQuery finalQuery = TaskQuery.create();
    setNewQueryCreated(isNewQueryCreated() || customTaskQuery == null || hasTaskId() || hasCaseId());

    if (!isNewQueryCreated()) {
      finalQuery.where().andOverall(customTaskQuery);
    }

    addTaskStateQuery(finalQuery);

    if (hasTaskId()) {
      finalQuery.where().and(queryForTaskId(getTaskId()));
      return finalQuery;
    }

    addCaseIdQuery(finalQuery);
    addKeywordQuery(finalQuery);
    addCategoryQuery(finalQuery);

    if (isSorted) {
      TaskSortingQueryAppender appender = new TaskSortingQueryAppender(finalQuery);
      finalQuery = appender.appendSorting(this).toQuery();
    }
    
    return finalQuery;
  }
  
  private void addCategoryQuery(TaskQuery finalQuery) {
    if (hasCategory()) {
      finalQuery.where().and(queryForCategory(getCategory()));
    }
  }
  
  private void addCaseIdQuery(TaskQuery finalQuery) {
    if (hasCaseId()) {
      if (isQueryByBusinessCaseId()) {
        finalQuery.where().and(
            TaskQuery.create().where().businessCaseId().isEqual(getCaseId()).or().caseId()
                .isEqual(getCaseId()));
      } else {
        finalQuery.where().and().caseId().isEqual(getCaseId());
      }
    }
  }

  private void addTaskStateQuery(TaskQuery finalQuery) {
    if (hasIncludedStates()) {
      finalQuery.where().and(queryForStates(getIncludedStates()));
    }
  }

  private void addKeywordQuery(TaskQuery finalQuery) {
    if (hasKeyword()) {
      finalQuery.where().and(queryForKeyword(getKeyword()));
    }
  }

  private TaskQuery queryForStates(List<TaskState> states) {
    TaskQuery stateFieldQuery = TaskQuery.create();
    IFilterQuery filterQuery = stateFieldQuery.where();
    for (TaskState state : states) {
      filterQuery.or().state().isEqual(state);
    }
    return stateFieldQuery;
  }

  private TaskQuery queryForCategory(String keyword) {
    String startingWithCategory = String.format("%s%%", keyword);
    return TaskQuery.create().where().category().isLike(startingWithCategory);
  }
  
  private TaskQuery queryForKeyword(String keyword) {
    String containingKeyword = String.format("%%%s%%", keyword.trim());
    TaskQuery filterByKeywordQuery = TaskQuery.create();
    searchScopeTaskFields = Optional.ofNullable(searchScopeTaskFields).orElse(new ArrayList<>());

    if (!isGlobalSearch || (isGlobalSearch && searchScopeTaskFields.contains(SearchScopeTaskField.NAME))) {
      filterByKeywordQuery.where().or().name().isLikeIgnoreCase(containingKeyword);
    }

    if (!isGlobalSearch || (isGlobalSearch && searchScopeTaskFields.contains(SearchScopeTaskField.DESCRIPTION))) {
      filterByKeywordQuery.where().or().description().isLikeIgnoreCase(containingKeyword);
    }

    try {
        long idKeyword = Long.parseLong(keyword.trim());
        String containingIdKeyword = String.format("%%%d%%", idKeyword);
        filterByKeywordQuery.where().or().taskId().isLike(containingIdKeyword);
      } catch (NumberFormatException e) {
        if (isGlobalSearch()) {
          String containingIdKeyword = String.format("%%%d%%", -1);
          filterByKeywordQuery.where().or().taskId().isLike(containingIdKeyword);
        }
      }
    return filterByKeywordQuery;
  }

  private TaskQuery queryForTaskId(Long taskId) {
    TaskQuery query = TaskQuery.create();
    query.where().taskId().isEqual(taskId);
    return query;
  }

  private class TaskSortingQueryAppender {

    private TaskQuery query;

    public TaskSortingQueryAppender(TaskQuery query) {
      this.query = query;
    }

    public TaskQuery toQuery() {
      return query;
    }

    public TaskSortingQueryAppender appendSorting(TaskSearchCriteria criteria) {
      appendSortByPriorityIfSet(criteria);
      appendSortByNameIfSet(criteria);
      appendSortByIdIfSet(criteria);
      appendSortByCreationDateIfSet(criteria);
      appendSortByExpiryDateIfSet(criteria);
      appendSortByCompletedDateIfSet(criteria);
      appendSortByStateIfSet(criteria);
      appendSortByCategoryIfSet(criteria);
      return this;
    }

    private void appendSortByPriorityIfSet(TaskSearchCriteria criteria) {
      if (TaskSortField.PRIORITY.toString().equalsIgnoreCase(criteria.getSortField())) {
        if (criteria.isSortDescending()) {
          query.orderBy().priority().descending();
        } else {
          query.orderBy().priority();
        }
      }
    }

    private void appendSortByNameIfSet(TaskSearchCriteria criteria) {
      if (TaskSortField.NAME.toString().equalsIgnoreCase(criteria.getSortField())) {
        if (criteria.isSortDescending()) {
          query.orderBy().name().descending();
        } else {
          query.orderBy().name();
        }
      }
    }

    private void appendSortByIdIfSet(TaskSearchCriteria criteria) {
      if (TaskSortField.ID.toString().equalsIgnoreCase(criteria.getSortField())) {
        if (criteria.isSortDescending()) {
          query.orderBy().taskId().descending();
        } else {
          query.orderBy().taskId();
        }
      }
    }

    private void appendSortByCreationDateIfSet(TaskSearchCriteria criteria) {
      if (TaskSortField.CREATION_TIME.toString().equalsIgnoreCase(criteria.getSortField())) {
        if (criteria.isSortDescending()) {
          query.orderBy().startTimestamp().descending();
        } else {
          query.orderBy().startTimestamp();
        }
      }
    }

    private void appendSortByExpiryDateIfSet(TaskSearchCriteria criteria) {
      if (TaskSortField.EXPIRY_TIME.toString().equalsIgnoreCase(criteria.getSortField())) {
        if (criteria.isQuickGlobalSearch()) {
          if (criteria.isSortDescending()) {
            query.orderBy().expiryTimestamp().descendingNullLast();
          } else {
            query.orderBy().expiryTimestamp().ascendingNullFirst();
          }
        } else {
          if (criteria.isSortDescending()) {
            query.orderBy().expiryTimestamp().descendingNullFirst();
          } else {
            query.orderBy().expiryTimestamp().ascendingNullLast();
          }
        }

      }
    }
    
    private void appendSortByCompletedDateIfSet(TaskSearchCriteria criteria) {
        if (TaskSortField.COMPLETED_ON.toString().equalsIgnoreCase(criteria.getSortField())) {
          if (criteria.isSortDescending()) {
            query.orderBy().endTimestamp().descendingNullFirst();
          } else {
            query.orderBy().endTimestamp().ascendingNullLast();
          }
        }
      }

    private void appendSortByStateIfSet(TaskSearchCriteria criteria) {
      if (TaskSortField.STATE.toString().equalsIgnoreCase(criteria.getSortField())) {
        if (criteria.isSortDescending()) {
          query.orderBy().state().descending();
        } else {
          query.orderBy().state();
        }
      }
    }
    
    private void appendSortByCategoryIfSet(@SuppressWarnings("unused") TaskSearchCriteria criteria) {
//      if (TaskSortField.CATEGORY.toString().equalsIgnoreCase(criteria.getSortField())) {
//        if (criteria.isSortDescending()) {
//          query.orderBy().category().descending();
//        } else {
//          query.orderBy().category();
//        }
//      }
    }
    
  }
  
  /** Check if current user can see task in advance state such as
   * DONE, DELAYED, DESTROYED, READY_FOR_JOIN
   * Then extend Search query for task criteria
   * @param isAdminPermission
   */
  public void extendStatesQueryByPermission(boolean isAdminPermission) {
    this.setAdminQuery(isAdminPermission);
    if (isOnlyShowOpenTask) {
      return;
    }
    if (isAdminPermission) {
      List<TaskState> adminStateNotIncluded = ADVANCE_STATES.stream()
          .filter(item -> !includedStates.contains(item)).collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(adminStateNotIncluded)) {
        addIncludedStates(adminStateNotIncluded);
      }
    }
  }

  public List<TaskState> getIncludedStates() {
    return includedStates;
  }

  public void setIncludedStates(List<TaskState> includedStates) {
    this.includedStates = includedStates;
  }

  public void addIncludedStates(List<TaskState> includedStates) {
    if (CollectionUtils.isEmpty(includedStates)) {
      this.includedStates = new ArrayList<>();
    }
    this.includedStates.addAll(includedStates);
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long taskId) {
    this.taskId = taskId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSortField() {
    return sortField;
  }

  public void setSortField(String sortField) {
    this.sortField = sortField;
  }

  public boolean isSortDescending() {
    return sortDescending;
  }

  public void setSortDescending(boolean sortDescending) {
    this.sortDescending = sortDescending;
  }

  public boolean isAdminQuery() {
    return isAdminQuery;
  }

  public void setAdminQuery(boolean isAdminQuery) {
    this.isAdminQuery = isAdminQuery;
  }

  public Long getCaseId() {
    return caseId;
  }

  public void setCaseId(Long caseId) {
    this.caseId = caseId;
  }

  public TaskAssigneeType getTaskAssigneeType() {
    return taskAssigneeType;
  }

  public void setTaskAssigneeType(TaskAssigneeType taskAssigneeType) {
    this.taskAssigneeType = taskAssigneeType;
  }
  
  public boolean isQueryByTaskId() {
    return isQueryByTaskId;
  }

  public void setQueryByTaskId(boolean isQueryByTaskId) {
    this.isQueryByTaskId = isQueryByTaskId;
  }

  public boolean isQueryByBusinessCaseId() {
    return isQueryByBusinessCaseId;
  }

  public void setQueryByBusinessCaseId(boolean isQueryByBusinessCaseId) {
    this.isQueryByBusinessCaseId = isQueryByBusinessCaseId;
  }

  public TaskQuery getCustomTaskQuery() {
    return customTaskQuery;
  }

  public void setCustomTaskQuery(TaskQuery customTaskQuery) {
    this.customTaskQuery = customTaskQuery;
  }

  public boolean hasIncludedStates() {
    return CollectionUtils.isNotEmpty(includedStates);
  }
  
  public boolean hasKeyword() {
    return StringUtils.isNotEmpty(keyword);
  }

  public boolean hasTaskId() {
    return taskId != null && taskId != 0;
  }
  
  public boolean hasCaseId() {
    return caseId != null && caseId != 0;
  }

  public boolean hasCategory() {
    return StringUtils.isNotBlank(category);
  }
  
  public boolean isSorted() {
    return isSorted;
  }

  public void setSorted(boolean isSorted) {
    this.isSorted = isSorted;
  }

  public boolean isNewQueryCreated() {
    return isNewQueryCreated;
  }

  public void setNewQueryCreated(boolean isNewQueryCreated) {
    this.isNewQueryCreated = isNewQueryCreated;
  }

  public boolean isGlobalSearch() {
    return isGlobalSearch;
  }

  public void setGlobalSearch(boolean isGlobalSearch) {
    this.isGlobalSearch = isGlobalSearch;
  }

  public boolean isQuickGlobalSearch() {
    return isGlobalSearch;
  }

  public List<SearchScopeTaskField> getSearchScopeTaskFields() {
    return searchScopeTaskFields;
  }

  public void setSearchScopeTaskFields(List<SearchScopeTaskField> searchScopeTaskFields) {
    this.searchScopeTaskFields = searchScopeTaskFields;
  }

  public boolean isGlobalSearchScope() {
    return isGlobalSearchScope;
  }

  public void setGlobalSearchScope(boolean isGlobalSearchScope) {
    this.isGlobalSearchScope = isGlobalSearchScope;
  }

  public boolean isOnlyShowOpenTask() {
    return isOnlyShowOpenTask;
  }

  public void setOnlyShowOpenTask(boolean isOnlyShowOpenTask) {
    this.isOnlyShowOpenTask = isOnlyShowOpenTask;
  }
}
