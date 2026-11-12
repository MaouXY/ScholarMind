package com.maou.apptemplateapi.module.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maou.apptemplateapi.module.literature.entity.ReadingHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingHistoryMapper extends BaseMapper<ReadingHistory> {

    /** 删除某篇文献的全部阅读历史（文献被删除时级联清理，避免残留孤儿数据）。 */
    @Delete("DELETE FROM reading_history WHERE literature_id = #{literatureId}")
    int physicalDeleteByLiteratureId(@Param("literatureId") Long literatureId);
}
