package com.maou.apptemplateapi.module.citation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maou.apptemplateapi.module.citation.entity.CitationRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CitationRecordMapper extends BaseMapper<CitationRecord> {

    /** 删除某篇文献关联的全部引用记录（文献被删除时级联清理）。 */
    @Delete("DELETE FROM citation_record WHERE literature_id = #{literatureId}")
    int physicalDeleteByLiteratureId(@Param("literatureId") Long literatureId);
}
