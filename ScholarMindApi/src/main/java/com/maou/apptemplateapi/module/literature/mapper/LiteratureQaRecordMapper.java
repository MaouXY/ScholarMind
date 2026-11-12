package com.maou.apptemplateapi.module.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maou.apptemplateapi.module.literature.entity.LiteratureQaRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LiteratureQaRecordMapper extends BaseMapper<LiteratureQaRecord> {

    /** 删除某篇文献关联的全部问答记录（文献被删除时级联清理）。 */
    @Delete("DELETE FROM literature_qa_record WHERE literature_id = #{literatureId}")
    int physicalDeleteByLiteratureId(@Param("literatureId") Long literatureId);
}
