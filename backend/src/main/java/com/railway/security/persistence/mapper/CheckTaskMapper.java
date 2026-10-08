package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.CheckTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CheckTaskMapper extends BaseMapper<CheckTask> {
    @Select("SELECT * FROM check_task WHERE id=#{id} AND deleted=0 FOR UPDATE")
    CheckTask selectForUpdate(@Param("id") Long id);
}
