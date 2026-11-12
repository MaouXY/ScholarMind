package com.maou.apptemplateapi.module.literature.event;

import com.maou.apptemplateapi.module.literature.service.LiteratureAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审核通过后顺带生成 AI 摘要（客户 2026-11-12 拍板：详情页不再提供「生成摘要」按钮，
 * 摘要改由审核通过时自动生成）。
 *
 * <p>和 {@link LiteraturePublishIndexListener} 同样的三条理由：避开 {@code LiteratureService} ↔
 * {@code LiteratureAiService} 的构造器循环依赖；{@code AFTER_COMMIT} 保证文献确实已是 PUBLISHED；
 * 摘要是旁路动作，失败只写 WARN 并把 {@code summary_status} 置为 FAILED，不影响审核结果。
 *
 * <p>顺序上排在索引监听器之后：摘要的输入是 {@code buildIndexContent}，里面会拼上已有的 AI 摘要，
 * 先有分块不影响摘要本身，但日志按「索引 → 摘要」排列更符合阅读顺序（Spring 内部按 bean 注册顺序触发，
 * 这里不额外声明 {@code @Order}，避免与实际触发顺序不一致的假象）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiteraturePublishSummaryListener {

    private final LiteratureAiService literatureAiService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSummaryRequested(LiteratureSummaryRequestedEvent event) {
        log.info("literature summary auto generation triggered, scenario=literature-summary-auto, literatureId={}, operatorId={}, trigger={}",
                event.literatureId(), event.operatorId(), event.trigger());
        try {
            literatureAiService.generateSummaryAutomatically(
                    event.literatureId(), event.operatorId(), event.organizationId());
        } catch (RuntimeException exception) {
            log.warn("literature summary auto generation failed, scenario=literature-summary-auto, literatureId={}, operatorId={}, trigger={}, reason={}",
                    event.literatureId(), event.operatorId(), event.trigger(), exception.getMessage());
        }
    }
}
