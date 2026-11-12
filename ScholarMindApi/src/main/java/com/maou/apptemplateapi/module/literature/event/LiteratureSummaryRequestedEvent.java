package com.maou.apptemplateapi.module.literature.event;

/**
 * 请求为文献生成 AI 摘要（客户确认：详情页不再提供「生成摘要」按钮，摘要改由审核通过时顺带生成）。
 *
 * <p>同样走事件而不是直接调用：{@code LiteratureService} 不能依赖 {@code LiteratureAiService}
 * （后者已经依赖前者），事件是这里唯一干净的解耦方式。
 *
 * @param literatureId  文献 ID
 * @param operatorId    触发生成的操作人（审核管理员）ID，用于日志溯源
 * @param organizationId 操作人所属组织，可为空；写入 AiTaskRecord 便于按组织统计
 * @param trigger       触发来源，目前只有 {@link #TRIGGER_PUBLISH}
 */
public record LiteratureSummaryRequestedEvent(Long literatureId,
                                              Long operatorId,
                                              Long organizationId,
                                              String trigger) {

    /** 审核通过后触发。 */
    public static final String TRIGGER_PUBLISH = "publish";
}
