package ch.ivy.addon.portalkit.bean;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.PF;

import com.axonivy.portal.components.publicapi.ProcessStartAPI;
import com.axonivy.portal.components.util.ProcessStartUtils;

import ch.ivy.addon.portal.generic.bean.UserMenuBean;
import ch.ivy.addon.portal.generic.navigation.PortalNavigator;
import ch.ivy.addon.portalkit.enums.PortalPermission;
import ch.ivy.addon.portalkit.jsf.ManagedBeans;
import ch.ivy.addon.portalkit.util.CaseUtils;
import ch.ivy.addon.portalkit.util.DateTimeFormatterUtils;
import ch.ivy.addon.portalkit.util.PermissionUtils;
import ch.ivy.addon.portalkit.util.PortalProcessViewerUtils;
import ch.ivy.addon.portalkit.util.TaskUtils;
import ch.ivy.addon.portalkit.util.TimesUtils;
import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.security.IPermission;
import ch.ivyteam.ivy.security.restricted.permission.IPermissionRepository;
import ch.ivyteam.ivy.workflow.ITask;
import ch.ivyteam.ivy.workflow.TaskState;

@ManagedBean
@ViewScoped
public class TaskActionBean implements Serializable {

  private static final long serialVersionUID = 7247809679085338843L;
  private boolean isShowResetTask;
  private boolean isShowReserveTask;
  private boolean isShowDelegateTask;
  //This variable control display of side step
  private boolean isShowAdditionalOptions;
  private boolean isShowDestroyTask;
  private boolean isShowReadWorkflowEvent;
  private static final String BACK_FROM_TASK_DETAILS = "Start Processes/PortalStart/BackFromTaskDetails.ivp";

  public TaskActionBean() {
    isShowResetTask = PermissionUtils.hasPortalPermission(PortalPermission.TASK_DISPLAY_RESET_ACTION);
    isShowReserveTask = PermissionUtils.hasPortalPermission(PortalPermission.TASK_DISPLAY_RESERVE_ACTION);
    isShowDelegateTask = PermissionUtils.hasPortalPermission(PortalPermission.TASK_DISPLAY_DELEGATE_ACTION);
    isShowAdditionalOptions = PermissionUtils.hasPortalPermission(PortalPermission.TASK_DISPLAY_ADDITIONAL_OPTIONS);
    isShowDestroyTask = PermissionUtils.hasPortalPermission(PortalPermission.TASK_DISPLAY_DESTROY_ACTION);
    isShowReadWorkflowEvent = PermissionUtils.hasPortalPermission(PortalPermission.TASK_DISPLAY_WORKFLOW_EVENT_ACTION);
  }

  public boolean canReset(ITask task) {
    return TaskUtils.canReset(task);
  }

  public boolean canPin(ITask task) {
    if (task == null) {
      return false;
    }
    return true;
  }

  public boolean canDelegate(ITask task) {
    if (task == null) {
        return false;
    }

    EnumSet<TaskState> invalidStates = EnumSet.of(
        TaskState.RESUMED, TaskState.DONE, TaskState.FAILED, 
        TaskState.DESTROYED, TaskState.CREATED, 
        TaskState.READY_FOR_JOIN, TaskState.JOIN_FAILED,
        TaskState.WAITING_FOR_INTERMEDIATE_EVENT
    );
    
    if (invalidStates.contains(task.getState())) {
        return false;
    }
    
    if (isCaseOwnerUser(task) && PermissionUtils.hasPortalPermission(PortalPermission.CASE_OWNER_TASK_DELEGATE)) {
      return hasPermission(task, IPermission.TASK_WRITE_ACTIVATOR);
    }
    
    if (userCanOnlyDelegateAssignedTask(task)) {
        return canResume(task);
    }
    
    return isNotDoneForWorkingUser(task) && 
           hasPermission(task, IPermission.TASK_WRITE_ACTIVATOR);
}

  private boolean userCanOnlyDelegateAssignedTask(ITask task) {
    IPermission permission =
        IPermissionRepository.instance().findByName(PortalPermission.TASK_WRITE_ACTIVATOR_OWN_TASKS.getValue());
    if (Objects.isNull(permission)) {
      return false;
    }
    return hasPermission(task, permission) && !hasPermission(task, IPermission.TASK_WRITE_ACTIVATOR);
  }

  public boolean canResume(ITask task) {
    return TaskUtils.canResume(task);
  }
  
  public boolean isCaseOwnerUser(ITask task) {
    return CaseUtils.isCaseOwnerUser(task.getCase());
  }

  public boolean canPark(ITask task) {
    if (task == null 
        || (task.getState() != TaskState.SUSPENDED && task.getState() != TaskState.CREATED && task.getState() != TaskState.RESUMED) 
        || (!canResume(task) && task.getState() != TaskState.CREATED)) {
      return false;
    }
    
    return hasPermission(task, IPermission.TASK_PARK_OWN_WORKING_TASK);
  }

  private static boolean hasPermission(ITask task, IPermission permission) {
    if (task == null || permission == null) {
      return false;
    }
    return PermissionUtils.hasPermission(permission);
  }

  public boolean canChangePriority(ITask task) {
    return isNotDone(task) && hasPermission(task, IPermission.TASK_WRITE_ORIGINAL_PRIORITY);
  }

  /**
   * Logic to check if user can change task expiry date:
   * - Task not null
   * - Task state is different from DONE, DESTROYED
   * - User has permission TaskWriteExpiryTimestamp
   * - The task has expiry handler or has expiry option 'Nobody & delete'
   * 
   * @param task
   * @return condition whether the user can change expiry time
   */
  
  public boolean canChangeExpiry(ITask task) {
    List<TaskState> nonChangeableExpiryStates = Arrays.asList(TaskState.DONE,
        TaskState.DESTROYED);

    if (task == null || nonChangeableExpiryStates.contains(task.getState())) {
      return false;
    }

    boolean isAutoDeleteAfterExpiry = CollectionUtils
        .isEmpty(task.expiry().responsibles().all())
        && StringUtils.isBlank(task.getExpiryTaskStartElementPid());

    boolean hasExpiryHandler = StringUtils
        .isNotBlank(task.getExpiryTaskStartElementPid());

    return hasPermission(task, IPermission.TASK_WRITE_EXPIRY_TIMESTAMP)
        && (isAutoDeleteAfterExpiry || hasExpiryHandler);
  }
  
  public boolean canChangeDelayTimestamp(ITask task) {
    if (TaskState.DELAYED != task.getState()) {
      return false;
    }
    return hasPermission(task, IPermission.TASK_WRITE_DELAY_TIMESTAMP);
  }
  
  public boolean canReadWorkflowEventTask() {
    return PermissionUtils.checkReadAllWorkflowEventPermission();
  }

  public boolean notHaveExpiryHandleLogic(ITask task) {
    return isNotDone(task) && hasPermission(task, IPermission.TASK_WRITE_EXPIRY_TIMESTAMP)
        && CollectionUtils.isEmpty(task.expiry().responsibles().all()) && StringUtils.isBlank(task.getExpiryTaskStartElementPid());
  }

  public boolean canChangeName(ITask task) {
    return isNotDone(task) && hasPermission(task, IPermission.TASK_WRITE_NAME);
  }

  public boolean canChangeDescription(ITask task) {
    return isNotDone(task) && hasPermission(task, IPermission.TASK_WRITE_DESCRIPTION);
  }

  public boolean canDestroyTask(ITask task) {
    List<TaskState> taskStates = Arrays.asList(TaskState.DONE, TaskState.DESTROYED);
    return hasPermission(task, IPermission.TASK_DESTROY) && !taskStates.contains(task.getState());
  }

  public boolean canChangeTaskExpiryActivator(ITask task) {
    return hasPermission(task, IPermission.TASK_WRITE_EXPIRY_ACTIVATOR) && isNotDone(task);
  }
  
  public boolean isNotDone(ITask task) {
    if (task == null) {
      return false;
    }
    EnumSet<TaskState> taskStates = EnumSet.of(TaskState.RESUMED, TaskState.PARKED, TaskState.SUSPENDED,
        TaskState.CREATED, TaskState.DELAYED);
    return taskStates.contains(task.getState());
  }
  
  public boolean isNotDoneForWorkingUser(ITask task) {
    if(PermissionUtils.checkReadAllTasksPermission()) {
      return true;
    }
    return isNotDone(task) && canResume(task);
  }
  
  public boolean canResumeForWorkingUser(ITask task) {
    if(PermissionUtils.checkReadAllTasksPermission()) {
      return true;
    }
    return canResume(task);
  }
  
  public boolean isTechnicalState(ITask task) {
    EnumSet<TaskState> taskStates = EnumSet.of(TaskState.WAITING_FOR_INTERMEDIATE_EVENT, TaskState.FAILED,
        TaskState.JOIN_FAILED);
    return taskStates.contains(task.getState());
  }
  
  public boolean showAdditionalOptions(ITask task) {
    return isShowAdditionalOptions && isNotDone(task) && canResumeForWorkingUser(task) && !isTechnicalState(task);
  }
  
  public boolean isShowResetTask() {
    return isShowResetTask;
  }

  public void setShowResetTask(boolean isShowResetTask) {
    this.isShowResetTask = isShowResetTask;
  }

  public boolean isShowReserveTask() {
    return isShowReserveTask;
  }

  public void setShowReserveTask(boolean isShowReserveTask) {
    this.isShowReserveTask = isShowReserveTask;
  }

  public boolean isShowDelegateTask() {
    return isShowDelegateTask;
  }

  public void setShowDelegateTask(boolean isShowDelegateTask) {
    this.isShowDelegateTask = isShowDelegateTask;
  }

  public boolean isShowAdditionalOptions() {
    return isShowAdditionalOptions;
  }

  public void setShowAdditionalOptions(boolean isShowAdditionalOptions) {
    this.isShowAdditionalOptions = isShowAdditionalOptions;
  }

  public boolean isShowDestroyTask() {
    return isShowDestroyTask;
  }

  public void setShowDestroyTask(boolean isShowDestroyTask) {
    this.isShowDestroyTask = isShowDestroyTask;
  }

  public boolean isShowReadWorkflowEvent() {
    return isShowReadWorkflowEvent;
  }

  public void setShowReadWorkflowEvent(boolean isShowReadWorkflowEvent) {
    this.isShowReadWorkflowEvent = isShowReadWorkflowEvent;
  }

  public void updateSelectedTaskItemId(boolean isShowInTaskList, Long taskId) {
    if (isShowInTaskList) {
      TaskWidgetBean taskWidgetBean = ManagedBeans.get("taskWidgetBean");
      if (taskWidgetBean != null) {
        taskWidgetBean.setSelectedTaskItemId(taskId);
      }
    }
  }

  public boolean showClearDelayTime(ITask task) {
    return TaskState.DELAYED.equals(task.getState()) && task.getDelayTimestamp() != null && isNotDoneForWorkingUser(task);
  }

  public boolean showClearExpiryTime(ITask task) {
    return canChangeExpiry(task) && task.getExpiryTimestamp() != null && isNotDoneForWorkingUser(task);
  }

  public boolean noActionAvailable(ITask task) {
    boolean hasWorkflowEventLink = isShowReadWorkflowEvent && canReadWorkflowEventTask();
    return !isNotDone(task) && !canResumeForWorkingUser(task) && !canReset(task) && !isTechnicalState(task)
        && !hasWorkflowEventLink && !showProcessViewer(task);
  }
  
  public void backToPrevPage(ITask task, boolean isFromTaskList, boolean isTaskStartedInDetails) {
    if (isFromTaskList || !isTaskStartedInDetails) {
      TaskUtils.updateTaskStartedAttribute(false);
      PF.current().executeScript("backToPrevPage();");
    } else {
      backToTaskList(task);
    }
  }
  
  private void backToTaskList(ITask task) {
    String friendlyRequestPath = ProcessStartUtils.findFriendlyRequestPathContainsKeyword("BackFromTaskDetails.ivp");
    if (StringUtils.isEmpty(friendlyRequestPath)) {
      friendlyRequestPath = BACK_FROM_TASK_DETAILS;
    }
    String requestPath = ProcessStartAPI.findRelativeUrlByProcessStartFriendlyRequestPath(friendlyRequestPath);
    if (StringUtils.isNotEmpty(requestPath)) {
      TaskUtils.updateTaskStartedAttribute(false);
      PortalNavigator.redirect(requestPath + "?endedTaskId=" + task.getId());
    }
  }

  public String getProcessViewerPageUri(ITask task) {
    return PortalProcessViewerUtils.getStartProcessViewerPageUri(task);
  }

  public String getDurationOfTask(ITask task) {
    return task.getEndTimestamp() != null ? getElapsedTimeForDoneTask(task) : getElapsedTimeForRunningTask(task);
  }

  private String getElapsedTimeForDoneTask(ITask task) {
    long duration = TimesUtils.getDurationInSeconds(task.getStartTimestamp(), task.getEndTimestamp());
    return DateTimeFormatterUtils.formatToShortTimeString(duration);
  }

  private String getElapsedTimeForRunningTask(ITask task) {
    long duration = TimesUtils.getDurationInSeconds(task.getStartTimestamp(), new Date());
    return DateTimeFormatterUtils.formatToShortTimeString(duration);
  }

  public String getDurationOfTaskOnTooltip(ITask task) {
    return task.getEndTimestamp() != null ? getElapsedTimeForDoneTaskOnTooltip(task)
        : getElapsedTimeForRunningTaskOnTooltip(task);
  }

  private String getElapsedTimeForDoneTaskOnTooltip(ITask task) {
    long duration = TimesUtils.getDurationInSeconds(task.getStartTimestamp(), task.getEndTimestamp());
    return DateTimeFormatterUtils.formatToExactTime(duration); 
  }

  private String getElapsedTimeForRunningTaskOnTooltip(ITask task) {
    long duration = TimesUtils.getDurationInSeconds(task.getStartTimestamp(), new Date());
    return DateTimeFormatterUtils.formatToExactTime(duration);
  }

  public boolean showProcessViewer(ITask task) {
    return ((UserMenuBean) ManagedBeans.get("userMenuBean")).isProcessViewerDisplayed(task.getCase().getBusinessCase());
  }
  
  public boolean canExpiry(ITask task) {
    return hasPermission(task, IPermission.TASK_WRITE_EXPIRY_ACTIVATOR) && isExpiryDateLower(task)
        && canExpiryTask(task);
  }

  public boolean isExpiryDateLower(ITask task) {
    return task.getExpiryTimestamp() != null && task.getExpiryTimestamp().after(new Date());
  }

  public boolean canExpiryTask(ITask task) {
    if (task == null) {
      return false;
    }
    EnumSet<TaskState> taskStates =
        EnumSet.of(TaskState.DONE, TaskState.DESTROYED, TaskState.RESUMED, TaskState.FAILED);
    return !taskStates.contains(task.getState());
  }

  public String getPinnedLabel(ITask task) {
    return TaskUtils.isPinnedTask(task) ? Ivy.cms().co("/ch.ivy.addon.portalkit.ui.jsf/common/unpin")
        : Ivy.cms().co("/ch.ivy.addon.portalkit.ui.jsf/common/pin");
  }

  public String getPinnedIcon(ITask task) {
    return TaskUtils.isPinnedTask(task) ? "option-action-icon si si-pin-bold" : "option-action-icon si si-pin";
  }

  public String getPinnedStyleClass(ITask task) {
    return "option-item ui-menu-items" + (TaskUtils.isPinnedTask(task) ? " color-destroy" : "");
  }

  public void markTaskAsPinned(ITask task) {
    TaskUtils.markTaskAsPinned(task);
  }

  public boolean isTaskPinned(ITask task) {
    return TaskUtils.isPinnedTask(task);
  }
}
