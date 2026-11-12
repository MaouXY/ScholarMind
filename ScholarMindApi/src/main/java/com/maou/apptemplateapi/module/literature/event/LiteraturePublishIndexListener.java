package com.maou.apptemplateapi.module.literature.event;

import com.maou.apptemplateapi.module.literature.service.LiteratureIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 自动重建文献索引（P3-1，客户需求「重建索引改为自动：上传/审核通过后自动重建」）。
 *
 * <p>为什么用 {@link TransactionalEventListener} 而不是直接调用：
 * <ol>
 *   <li>避开 {@code LiteratureService} 与 {@code LiteratureIndexService} 的构造器循环依赖；</li>
 *   <li>{@link TransactionPhase#AFTER_COMMIT} 保证「文献确实已经落库」之后才建索引，
 *       业务事务万一回滚就不会留下孤儿分块；</li>
 *   <li>索引是旁路动作：失败只记 WARN，不影响上传 / 审核结果——用户该看到的响应已经返回。</li>
 * </ol>
 *
 * <p>故意不加 {@code @Async}：这里跑在请求线程上、但已脱离原事务，重建（含 RustFS 取对象与
 * Milvus 写入）本来就要几秒，异步化会引入线程池与 SecurityContext 传播问题；
 * 内存与并发压力在毕设规模下不构成问题，换来的是日志顺序清晰、排障简单。
 *
 * <p>{@code fallbackExecution = true} 让没有事务的调用（例如脚本直接 approve）也能触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiteraturePublishIndexListener {

    private final LiteratureIndexService literatureIndexService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIndexRequested(LiteratureIndexRequestedEvent event) {
        log.info("literature index auto rebuild triggered, scenario=literature-index-auto-rebuild, literatureId={}, operatorId={}, trigger={}",
                event.literatureId(), event.operatorId(), event.trigger());
        try {
            literatureIndexService.rebuildAutomatically(event.literatureId(), event.operatorId());
        } catch (RuntimeException exception) {
            // 索引是旁路动作：上传/审核已经成功，这里失败只降级，不能把异常抛回请求线程
            log.warn("literature index auto rebuild failed, scenario=literature-index-auto-rebuild, literatureId={}, operatorId={}, trigger={}, reason={}",
                    event.literatureId(), event.operatorId(), event.trigger(), exception.getMessage());
        }
    }
}
