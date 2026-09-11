package com.maou.apptemplateapi.module.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LiteratureChunkMapper extends BaseMapper<LiteratureChunk> {

    @Delete("DELETE FROM literature_chunk WHERE literature_id = #{literatureId}")
    int physicalDeleteByLiteratureId(@Param("literatureId") Long literatureId);
}
