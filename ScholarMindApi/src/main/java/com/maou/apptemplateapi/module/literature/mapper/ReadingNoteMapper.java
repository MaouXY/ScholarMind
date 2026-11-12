package com.maou.apptemplateapi.module.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maou.apptemplateapi.module.literature.entity.ReadingNote;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingNoteMapper extends BaseMapper<ReadingNote> {

    /** 删除某篇文献的全部阅读笔记（文献被删除时级联清理）。 */
    @Delete("DELETE FROM reading_note WHERE literature_id = #{literatureId}")
    int physicalDeleteByLiteratureId(@Param("literatureId") Long literatureId);
}
