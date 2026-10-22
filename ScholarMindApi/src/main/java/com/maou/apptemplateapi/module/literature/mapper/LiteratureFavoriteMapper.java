package com.maou.apptemplateapi.module.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maou.apptemplateapi.module.literature.entity.LiteratureFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LiteratureFavoriteMapper extends BaseMapper<LiteratureFavorite> {

    /**
     * 物理删除某用户对某文献的收藏记录（连同历史逻辑删除的残留行）。
     *
     * 背景：表上的唯一索引是 (user_id, literature_id, deleted)。若取消收藏走逻辑删除，
     * 反复「收藏 → 取消 → 收藏 → 取消」时，第二次取消会把 deleted 再置为 1，
     * 与上一轮遗留的 deleted=1 行冲突，抛 DuplicateKeyException，对外表现为 HTTP 500。
     * 收藏记录不需要审计留痕，因此取消收藏改为物理删除，保证任意次切换都幂等。
     */
    @Delete("DELETE FROM literature_favorite WHERE user_id = #{userId} AND literature_id = #{literatureId}")
    int hardDeleteByUserAndLiterature(@Param("userId") Long userId, @Param("literatureId") Long literatureId);
}
