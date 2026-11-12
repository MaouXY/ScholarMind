package com.maou.apptemplateapi.module.literature.event;

/**
 * 请求自动重建文献索引（P3-1，客户需求「重建索引改为自动：上传/审核通过后自动重建」）。
 *
 * <p>用事件而不是直接调用，是为了避开 LiteratureService ↔ LiteratureIndexService 的构造器循环依赖
 * （索引服务需要 LiteratureService 做可读性校验与读写文献，反向再注入就成环）。
 *
 * @param literatureId 需要建索引的文献
 * @param operatorId   触发者（上传者或审核管理员）
 * @param trigger      触发点，upload / publish，仅用于日志区分
 */
public record LiteratureIndexRequestedEvent(Long literatureId, Long operatorId, String trigger) {

    public static final String TRIGGER_UPLOAD = "upload";
    public static final String TRIGGER_PUBLISH = "publish";
}
